package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.BlockedDevice;
import dev.workflowcrm.auth.domain.DeviceTrust;
import dev.workflowcrm.auth.domain.LoginAttemptTracker;
import dev.workflowcrm.auth.domain.SessionMetadata;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(
        properties = {
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test",
            "app.minio.secret-key=test-secret",
            "app.security.jwt.secret=test-jwt-signing-secret-with-32-bytes",
            "app.security.jwt.issuer=workflow-admin-security-test",
            "app.security.jwt.secure=true",
            "app.security.telegram.bot-username=workflow_test_bot",
            "app.security.telegram.bot-token=test-bot-token",
            "app.security.telegram.webhook-secret=test-webhook-secret"
        })
@Import(AuthLoginIT.ProtectedEndpointConfiguration.class)
class AdminSecurityIT {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0632-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0632-000000000002");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0632-000000000003");
    private static final Cookie USER_DEVICE = deviceCookie((byte) 1);
    private static final Cookie ADMIN_DEVICE = deviceCookie((byte) 2);
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "test-password";
    private static final String ACCESS = "workflow_access_token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private DeviceCookie identity;

    @Autowired
    private DeviceTrust devices;

    @Autowired
    private LoginAttemptTracker attempts;

    @Autowired
    private AuthLoginIT.FakeTelegramSender telegram;

    @Autowired
    private AuthLoginIT.FakeEmailSender email;

    private Cookie administrator;
    private Csrf csrf;

    @BeforeEach
    void setUp() throws Exception {
        database.update("delete from security_unlock_events");
        database.update("delete from users");
        try (var connection =
                java.util.Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushDb();
        }
        telegram.reset();
        email.reset();
        createUser(USER, EMAIL, "MANAGER");
        createUser(OTHER, "other@example.com", "MANAGER");
        createUser(ADMIN, "admin@example.com", "SUPERADMIN");
        devices.trust(ADMIN, identity.deviceId(ADMIN_DEVICE.getValue()));
        administrator = login("admin@example.com", PASSWORD, ADMIN_DEVICE)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(ACCESS);
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf")).andReturn();
        var body = json.readTree(csrfResult.getResponse().getContentAsByteArray());
        csrf = new Csrf(
                csrfResult.getResponse().getCookie("XSRF-TOKEN"),
                body.get("headerName").asText(),
                body.get("token").asText());
    }

    @Test
    void listsRejectedDevicesAndUnblocksWithoutRestoringTrustOrSessions() throws Exception {
        String deviceId = identity.deviceId(USER_DEVICE.getValue());
        devices.trust(USER, deviceId);
        MvcResult oldLogin =
                login(EMAIL, PASSWORD, USER_DEVICE).andExpect(status().isOk()).andReturn();
        redis.delete("auth:trusted-device:" + USER + ":" + deviceId);
        MvcResult pending = login(EMAIL, PASSWORD, USER_DEVICE)
                .andExpect(status().isAccepted())
                .andReturn();
        String challenge = json.readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        mvc.perform(post("/api/auth/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "callback_query",
                                Map.of(
                                        "data",
                                        "auth:reject:" + challenge,
                                        "from",
                                        Map.of("id", USER.getLeastSignificantBits()))))))
                .andExpect(status().isNoContent());
        devices.block(OTHER, new SessionMetadata(deviceId, "Other browser", "192.0.2.2"));

        MvcResult listing = mvc.perform(
                        get("/api/users/{id}/blocked-devices", USER).cookie(administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userAgent").value("Test browser"))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].blockedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].deviceId").doesNotExist())
                .andReturn();
        assertThat(listing.getResponse().getContentAsString()).doesNotContain(deviceId, "auth:");
        String blockId = json.readTree(listing.getResponse().getContentAsByteArray())
                .get(0)
                .get("id")
                .asText();
        change("/blocked-devices/" + blockId + "/unblock", USER).andExpect(status().isNoContent());
        assertThat(devices.blocked(USER, deviceId)).isFalse();
        assertThat(devices.trusted(USER, deviceId)).isFalse();
        assertThat(devices.blocked(OTHER, deviceId)).isTrue();
        mvc.perform(get("/api/auth/me").cookie(oldLogin.getResponse().getCookie(ACCESS)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .cookie(oldLogin.getResponse().getCookie("workflow_refresh_token"), csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/{id}/blocked-devices", USER).cookie(administrator))
                .andExpect(jsonPath("$.length()").value(0));
        assertAudit("DEVICE_UNBLOCK", "APPLIED", UUID.fromString(blockId));
        change("/blocked-devices/" + blockId + "/unblock", USER).andExpect(status().isNotFound());
        redis.delete("auth:challenge-rate:" + USER);
        login(EMAIL, PASSWORD, USER_DEVICE)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_TELEGRAM"));
    }

    @Test
    void removesTemporaryLockEarlyAndRestartsFailureCounter() throws Exception {
        devices.trust(USER, identity.deviceId(USER_DEVICE.getValue()));
        for (int i = 0; i < 5; i++) {
            login("USER@EXAMPLE.COM", "wrong", USER_DEVICE).andExpect(status().isUnauthorized());
        }
        login(EMAIL, PASSWORD, USER_DEVICE).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/users/{id}/login-lock", USER).cookie(administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.failedAttempts").value(5))
                .andExpect(jsonPath("$.maxAttempts").value(5))
                .andExpect(jsonPath("$.lockDurationSeconds").value(900))
                .andExpect(jsonPath("$.blockedUntil").isNotEmpty());
        assertThat(attempts.lock(EMAIL).blockedUntil())
                .isBetween(Instant.now().plusSeconds(870), Instant.now().plusSeconds(901));
        change("/login-lock/unlock", USER).andExpect(status().isNoContent());
        assertAudit("LOGIN_UNLOCK", "APPLIED", null);
        assertThat(attempts.lock(EMAIL).failedAttempts()).isZero();
        login(EMAIL, "wrong", USER_DEVICE).andExpect(status().isUnauthorized());
        assertThat(attempts.lock(EMAIL).failedAttempts()).isEqualTo(1);
        login(EMAIL, PASSWORD, USER_DEVICE).andExpect(status().isOk());
        change("/login-lock/unlock", USER).andExpect(status().isConflict());
        assertThat(database.queryForObject("select count(*) from security_unlock_events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unlockDoesNotEnableDisabledAccountOrRemoveDeviceBlock() throws Exception {
        for (int i = 0; i < 5; i++) {
            attempts.registerFailure(EMAIL);
        }
        String deviceId = identity.deviceId(USER_DEVICE.getValue());
        devices.block(USER, deviceId);
        database.update("update users set status = 'DISABLED' where id = ?", USER);
        change("/login-lock/unlock", USER).andExpect(status().isNoContent());
        assertThat(devices.blocked(USER, deviceId)).isTrue();
        login(EMAIL, PASSWORD, USER_DEVICE).andExpect(status().isForbidden());
        assertThat(database.queryForObject("select status from users where id = ?", String.class, USER))
                .isEqualTo("DISABLED");
    }

    @Test
    void legacyBlocksRemainVisibleAndTheirTtlIsPreserved() throws Exception {
        String key = "auth:blocked-device:" + USER + ":" + identity.deviceId(USER_DEVICE.getValue());
        redis.opsForValue().set(key, "1", Duration.ofMinutes(2));
        long ttl = redis.getExpire(key);
        mvc.perform(get("/api/users/{id}/blocked-devices", USER).cookie(administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userAgent").value(""))
                .andExpect(jsonPath("$[0].blockedAt").isEmpty());
        BlockedDevice block = devices.blockedDevices(USER).getFirst();
        assertThat(devices.blockedDevices(USER).getFirst().id()).isEqualTo(block.id());
        assertThat(redis.getExpire(key)).isBetween(ttl - 3, ttl);
        change("/blocked-devices/" + block.id() + "/unblock", USER).andExpect(status().isNoContent());
    }

    @Test
    void staleBlockIdentifierCannotRemoveNewBlockOfSameDevice() throws Exception {
        String deviceId = identity.deviceId(USER_DEVICE.getValue());
        devices.block(USER, deviceId);
        BlockedDevice old = devices.blockedDevices(USER).getFirst();
        devices.block(USER, deviceId);
        assertThat(devices.unblock(USER, old)).isFalse();
        change("/blocked-devices/" + old.id() + "/unblock", USER).andExpect(status().isNotFound());
        assertThat(devices.blocked(USER, deviceId)).isTrue();
        assertThat(database.queryForObject("select count(*) from security_unlock_events", Integer.class))
                .isZero();
    }

    @Test
    void expiredLocksAndDevicesDisappearWithoutUnlockingOtherState() throws Exception {
        for (int i = 0; i < 5; i++) {
            attempts.registerFailure(EMAIL);
        }
        String attemptsKey = redis.keys("auth:login-attempts:*").iterator().next();
        redis.expire(attemptsKey, Duration.ofMillis(10));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redis.hasKey(attemptsKey)));
        mvc.perform(get("/api/users/{id}/login-lock", USER).cookie(administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.failedAttempts").value(0))
                .andExpect(jsonPath("$.blockedUntil").isEmpty());
        String deviceId = identity.deviceId(USER_DEVICE.getValue());
        devices.block(USER, deviceId);
        UUID blockId = devices.blockedDevices(USER).getFirst().id();
        String blockKey = "auth:blocked-device:" + USER + ":" + deviceId;
        redis.expire(blockKey, Duration.ofMillis(10));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redis.hasKey(blockKey)));
        change("/blocked-devices/" + blockId + "/unblock", USER).andExpect(status().isNotFound());
        assertThat(devices.blockedDevices(USER)).isEmpty();
    }

    @Test
    void endpointsRejectMissingAuthenticationRegularUsersAndDemotedAdministrators() throws Exception {
        mvc.perform(get("/api/users/{id}/blocked-devices", USER)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/{id}/login-lock", USER)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/{id}/login-lock/unlock", USER)
                        .cookie(csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isUnauthorized());
        devices.trust(USER, identity.deviceId(USER_DEVICE.getValue()));
        Cookie manager = login(EMAIL, PASSWORD, USER_DEVICE)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(ACCESS);
        for (Cookie caller : List.of(manager, administrator)) {
            if (caller == administrator) {
                database.update(
                        "update users set role_id = (select id from roles where code = 'MANAGER') where id = ?", ADMIN);
            }
            mvc.perform(get("/api/users/{id}/blocked-devices", OTHER).cookie(caller))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/users/{id}/login-lock", OTHER).cookie(caller)).andExpect(status().isForbidden());
            mvc.perform(post("/api/users/{id}/login-lock/unlock", OTHER)
                            .cookie(caller, csrf.cookie())
                            .header(csrf.header(), csrf.token()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/users/{id}/blocked-devices/{block}/unblock", OTHER, UUID.randomUUID())
                            .cookie(caller, csrf.cookie())
                            .header(csrf.header(), csrf.token()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void mutationsRequireCsrfAndDisabledAdministratorsAreDenied() throws Exception {
        devices.block(USER, identity.deviceId(USER_DEVICE.getValue()));
        UUID block = devices.blockedDevices(USER).getFirst().id();
        mvc.perform(post("/api/users/{id}/blocked-devices/{block}/unblock", USER, block)
                        .cookie(administrator))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/{id}/login-lock/unlock", USER).cookie(administrator))
                .andExpect(status().isForbidden());
        database.update("update users set status = 'DISABLED' where id = ?", ADMIN);
        change("/blocked-devices/" + block + "/unblock", USER).andExpect(status().isForbidden());
        assertThat(database.queryForObject("select count(*) from security_unlock_events", Integer.class))
                .isZero();
    }

    @Test
    void missingUsersForeignBlocksAndInvalidIdsDoNotChangeState() throws Exception {
        devices.block(OTHER, identity.deviceId(USER_DEVICE.getValue()));
        UUID otherBlock = devices.blockedDevices(OTHER).getFirst().id();
        change("/blocked-devices/" + otherBlock + "/unblock", USER).andExpect(status().isNotFound());
        change("/login-lock/unlock", UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}/blocked-devices", UUID.randomUUID()).cookie(administrator))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}/login-lock", UUID.randomUUID()).cookie(administrator))
                .andExpect(status().isNotFound());
        change("/blocked-devices/not-a-uuid/unblock", USER).andExpect(status().isBadRequest());
        assertThat(devices.blockedDevices(OTHER)).hasSize(1);
    }

    @Test
    void auditWriteFailureKeepsBothDeviceAndLoginBlocked() throws Exception {
        devices.block(USER, identity.deviceId(USER_DEVICE.getValue()));
        UUID blockId = devices.blockedDevices(USER).getFirst().id();
        for (int i = 0; i < 5; i++) {
            attempts.registerFailure(EMAIL);
        }
        database.execute(
                "alter table security_unlock_events add constraint reject_unlock_event check (false) not valid");
        try {
            change("/blocked-devices/" + blockId + "/unblock", USER).andExpect(status().isServiceUnavailable());
            change("/login-lock/unlock", USER).andExpect(status().isServiceUnavailable());
            assertThat(devices.blockedDevices(USER)).hasSize(1);
            assertThat(attempts.isBlocked(EMAIL)).isTrue();
        } finally {
            database.execute("alter table security_unlock_events drop constraint reject_unlock_event");
        }
    }

    @Test
    void resultWriteFailureLeavesDurableRequestAndDoesNotReportSuccess() throws Exception {
        for (int i = 0; i < 5; i++) {
            attempts.registerFailure(EMAIL);
        }
        database.execute(
                "alter table security_unlock_events add constraint reject_unlock_result check (outcome = 'REQUESTED') not valid");
        try {
            change("/login-lock/unlock", USER).andExpect(status().isServiceUnavailable());
            assertThat(attempts.isBlocked(EMAIL)).isFalse();
            assertAudit("LOGIN_UNLOCK", "REQUESTED", null);
        } finally {
            database.execute("alter table security_unlock_events drop constraint reject_unlock_result");
        }
    }

    @Test
    void limitsAdministrativeActionsWithoutClearingTheNextLock() throws Exception {
        for (int action = 0; action < 10; action++) {
            for (int i = 0; i < 5; i++) {
                attempts.registerFailure(EMAIL);
            }
            change("/login-lock/unlock", USER).andExpect(status().isNoContent());
        }
        for (int i = 0; i < 5; i++) {
            attempts.registerFailure(EMAIL);
        }
        change("/login-lock/unlock", USER)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("REQUEST_RATE_LIMITED"));
        assertThat(attempts.isBlocked(EMAIL)).isTrue();
        assertThat(database.queryForObject(
                        "select count(*) from security_unlock_events where outcome = 'APPLIED'", Integer.class))
                .isEqualTo(10);
    }

    private void assertAudit(String action, String outcome, UUID deviceId) {
        var records = database.queryForList(
                "select administrator_id, user_id, action, blocked_device_id, outcome from security_unlock_events");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst())
                .containsEntry("administrator_id", ADMIN)
                .containsEntry("user_id", USER)
                .containsEntry("action", action)
                .containsEntry("blocked_device_id", deviceId)
                .containsEntry("outcome", outcome);
    }

    private ResultActions change(String suffix, UUID user) throws Exception {
        return mvc.perform(post("/api/users/" + user + suffix)
                .cookie(administrator, csrf.cookie())
                .header(csrf.header(), csrf.token()));
    }

    private ResultActions login(String address, String password, Cookie device) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .cookie(device)
                .header("User-Agent", "Test browser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", address, "password", password))));
    }

    private void createUser(UUID id, String address, String role) {
        database.update(
                "insert into users (id, email, password_hash, full_name, role_id, status, telegram_chat_id) values (?, ?, ?, 'Security User', (select id from roles where code = ?), 'ACTIVE', ?)",
                id,
                address,
                passwords.encode(PASSWORD),
                role,
                id.getLeastSignificantBits());
    }

    private static Cookie deviceCookie(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return new Cookie(
                "workflow_device", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    private record Csrf(Cookie cookie, String header, String token) {}
}
