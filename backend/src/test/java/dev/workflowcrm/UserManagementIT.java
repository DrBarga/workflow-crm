package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.EmailChallengeSender;
import dev.workflowcrm.auth.domain.TelegramBotGateway;
import dev.workflowcrm.auth.domain.TelegramChallengeSender;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import dev.workflowcrm.users.domain.UserInvitationSender;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
            "app.security.jwt.issuer=workflow-users-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.jwt.secure=true",
            "app.users.invitation-ttl=72h",
            "app.users.invitation-url=http://localhost/set-password",
            "app.users.mail-from=test@example.com",
            "app.security.telegram.bot-username=workflow_test_bot",
            "app.security.telegram.bot-token=test-bot-token",
            "app.security.telegram.webhook-secret=test-webhook-secret"
        })
@Import(UserManagementIT.InvitationTestConfiguration.class)
class UserManagementIT {

    private static final String ACCESS = "workflow_access_token";
    private static final Cookie DEVICE = new Cookie(
            "workflow_device", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0003-000000000001");
    private static final UUID RESOURCE_REGION = UUID.fromString("00000000-0000-0000-0003-000000000002");

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
    private PasswordEncoder passwords;

    @Autowired
    private DeviceCookie deviceCookie;

    @Autowired
    private ObjectMapper json;

    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0003-000000000004");
    private static final UUID OTHER_REGION = UUID.fromString("00000000-0000-0000-0003-000000000005");
    private Cookie admin;
    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        database.execute(
                "create table if not exists test_object_links (id uuid primary key, manager_id uuid references users(id), region_id uuid references regions(id))");
        database.update("delete from test_object_links");
        database.update("delete from user_invitations");
        database.update("delete from password_setup_events");
        database.update("delete from user_access_history");
        database.update("delete from telegram_binding_events");
        database.update("delete from users");
        database.update("delete from regions");
        database.update(
                "insert into regions(id, code, name) values (?, 'ONE', 'One'), (?, 'TWO', 'Two')",
                RESOURCE_REGION,
                OTHER_REGION);
        insertUser(USER_ID, "admin@example.com", "SUPERADMIN", RESOURCE_REGION, "ACTIVE");
        insertUser(MANAGER_ID, "manager@example.com", "MANAGER", RESOURCE_REGION, "ACTIVE");
        try (var connection =
                java.util.Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushDb();
        }
        InvitationTestConfiguration.tokens.clear();
        InvitationTestConfiguration.botMessages.clear();
        InvitationTestConfiguration.emailCodes.clear();
        InvitationTestConfiguration.passwordDeliveries.set(0);
        InvitationTestConfiguration.failPassword = false;
        admin = login("admin@example.com");
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        csrfToken = json.readTree(csrf.getResponse().getContentAsByteArray())
                .get("token")
                .asText();
    }

    @Test
    void createsDisabledAccountWithNormalizedEmailAndNoCredentialsInResponse() throws Exception {
        var result = write(post("/api/users"), creation(" New@Example.com "), admin)
                .andExpect(status().isCreated())
                .andReturn();
        var user = json.readTree(result.getResponse().getContentAsByteArray());
        UUID id = UUID.fromString(user.get("id").asText());
        assertThat(user.get("email").asText()).isEqualTo("new@example.com");
        assertThat(user.get("status").asText()).isEqualTo("DISABLED");
        assertThat(user.get("passwordSet").asBoolean()).isFalse();
        assertThat(user.get("telegramLinked").asBoolean()).isFalse();
        assertThat(user.get("fullName").asText()).isEqualTo("New User");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/users/" + id);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash", "tokenHash");
        assertThat(database.queryForObject("select password_hash from users where id=?", String.class, id))
                .startsWith("$2a$12$");
        assertThat(database.queryForObject(
                        "select count(*) from user_access_history where user_id=? and actor_id=? and action='CREATED'",
                        Integer.class,
                        id,
                        USER_ID))
                .isEqualTo(1);
        mvc.perform(get("/api/users/" + id).cookie(admin)).andExpect(status().isOk());
        String invitationToken = InvitationTestConfiguration.tokens.get("new@example.com");
        assertThat(invitationToken).isNotBlank();
        assertThat(database.queryForObject("select token_hash from user_invitations where user_id=?", String.class, id))
                .hasSize(64)
                .isNotEqualTo(invitationToken);
        assertThat(InvitationTestConfiguration.passwordDeliveries).hasValue(1);
        assertThat(database.queryForObject(
                        "select extract(epoch from (expires_at-created_at))/3600 from user_invitations where user_id=?",
                        Double.class,
                        id))
                .isBetween(71.99, 72.01);
    }

    @Test
    void invitationIsSingleUseAndResendInvalidatesPreviousLink() throws Exception {
        var created = write(post("/api/users"), creation("invite@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String first = InvitationTestConfiguration.tokens.get("invite@example.com");
        write(post("/api/users/" + id + "/invitation"), Map.of(), admin).andExpect(status().isNoContent());
        String second = InvitationTestConfiguration.tokens.get("invite@example.com");
        assertThat(second).isNotEqualTo(first);
        write(patch("/api/users/" + id), Map.of("status", "ACTIVE"), admin).andExpect(status().isConflict());

        write(post("/api/user-invitations/accept"), Map.of("token", first, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isGone());
        var accepted = write(
                        post("/api/user-invitations/accept"), Map.of("token", second, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie device = accepted.getResponse().getCookie("workflow_device");
        assertThat(device).isNotNull();
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceCookie.deviceId(device.getValue())))
                .isTrue();
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(loginWithPassword("invite@example.com", "N7!mK4#qR9@x")).isNotNull();
        write(post("/api/user-invitations/accept"), Map.of("token", second, "password", "another-password"), null)
                .andExpect(status().isGone());
    }

    @Test
    void invitationRejectsWeakPasswordWithoutConsumingTheLink() throws Exception {
        var created = write(post("/api/users"), creation("policy@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String token = InvitationTestConfiguration.tokens.get("policy@example.com");
        write(post("/api/user-invitations/accept"), Map.of("token", token, "password", "Password123!"), null)
                .andExpect(status().isBadRequest());
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("DISABLED");
        assertThat(database.queryForObject(
                        "select consumed_at from user_invitations where user_id=?", java.time.OffsetDateTime.class, id))
                .isNull();
        write(post("/api/user-invitations/accept"), Map.of("token", token, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isNoContent());
    }

    @Test
    void authInvitationEndpointsTrustTheSubmittingBrowserAndWriteAuditEvents() throws Exception {
        UUID id = createInvitedUser("new-flow@example.com");
        String token = InvitationTestConfiguration.tokens.get("new-flow@example.com");
        Cookie staleAccess = new Cookie(ACCESS, "expired-access-token");
        var opened = mvc.perform(get("/api/auth/invite/{token}", token).cookie(staleAccess))
                .andExpect(status().isOk())
                .andReturn();
        Cookie device = opened.getResponse().getCookie("workflow_device");
        assertThat(device).isNotNull();
        assertThat(device.isHttpOnly()).isTrue();
        assertThat(opened.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(redis.hasKey(trustKey(id, device))).isFalse();

        write(
                        post("/api/auth/set-password").cookie(device, staleAccess),
                        Map.of("token", token, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        assertThat(redis.hasKey(trustKey(id, device))).isTrue();
        assertThat(database.queryForList("select action from password_setup_events where user_id=?", String.class, id))
                .containsExactlyInAnyOrder("INITIAL_PASSWORD_SET", "INVITATION_DEVICE_TRUSTED");
        assertThat(database.queryForObject(
                        "select count(distinct device_id) from password_setup_events where user_id=?",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(database.queryForObject(
                        "select device_id from password_setup_events where user_id=? limit 1", String.class, id))
                .isEqualTo(deviceCookie.deviceId(device.getValue()));
        mvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "new-flow@example.com", "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isOk());

        Cookie other = new Cookie("workflow_device", deviceCookie.newValue());
        mvc.perform(get("/api/auth/invite/{token}", token).cookie(other)).andExpect(status().isGone());
        write(post("/api/auth/set-password").cookie(other), Map.of("token", token, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isGone());
        assertThat(redis.hasKey(trustKey(id, other))).isFalse();
        assertThat(database.queryForObject(
                        "select count(*) from password_setup_events where user_id=?", Integer.class, id))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "Ab1!short,Password must contain 12 to 128 characters",
        "lowercase123!,Password must include an uppercase Latin letter",
        "UPPERCASE123!,Password must include a lowercase Latin letter",
        "WithoutDigits!,Password must include a digit",
        "WithoutSymbols12,Password must include a special character from !@#$%^&*()_+-=",
        "Password123!,Password must not contain common passwords",
        "New!K9283746x,Password must not contain your email or name",
        "workflow!9283746,Password must not contain Workflow"
    })
    void authPasswordSetupReportsTheViolatedRuleWithoutChangingTheAccount(String password, String expectedMessage)
            throws Exception {
        UUID id = createInvitedUser("new@example.com");
        String token = InvitationTestConfiguration.tokens.get("new@example.com");
        Cookie device = new Cookie("workflow_device", deviceCookie.newValue());
        var rejected = write(
                        post("/api/auth/set-password").cookie(device),
                        Map.of("token", token, "password", password),
                        null)
                .andExpect(status().isBadRequest())
                .andReturn();
        var body = json.readTree(rejected.getResponse().getContentAsByteArray());
        assertThat(body.get("violations").get(0).get("field").asText()).isEqualTo("password");
        assertThat(body.get("violations").get(0).get("message").asText()).isEqualTo(expectedMessage);
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("DISABLED");
        assertThat(database.queryForObject(
                        "select consumed_at from user_invitations where user_id=?", java.time.OffsetDateTime.class, id))
                .isNull();
        assertThat(redis.hasKey(trustKey(id, device))).isFalse();
        assertThat(database.queryForObject(
                        "select count(*) from password_setup_events where user_id=?", Integer.class, id))
                .isZero();
        mvc.perform(get("/api/auth/invite/{token}", token)).andExpect(status().isOk());
    }

    @Test
    void invalidAndExpiredAuthInvitationsNeverIssueDeviceCookies() throws Exception {
        UUID id = createInvitedUser("expired-auth@example.com");
        String token = InvitationTestConfiguration.tokens.get("expired-auth@example.com");
        database.update("update user_invitations set expires_at=now()-interval '1 second' where user_id=?", id);
        for (String invalid : new String[] {token, "unknown-token"}) {
            var result = mvc.perform(get("/api/auth/invite/{token}", invalid))
                    .andExpect(status().isGone())
                    .andReturn();
            assertThat(result.getResponse().getCookie("workflow_device")).isNull();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(invalid);
            write(post("/api/auth/set-password"), Map.of("token", invalid, "password", "N7!mK4#qR9@x"), null)
                    .andExpect(status().isGone());
        }
    }

    @Test
    void passwordSetupRequiresCsrfAndCanIssueItsOwnDeviceCookie() throws Exception {
        UUID id = createInvitedUser("direct-setup@example.com");
        String token = InvitationTestConfiguration.tokens.get("direct-setup@example.com");
        mvc.perform(post("/api/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isForbidden());
        var accepted = write(post("/api/auth/set-password"), Map.of("token", token, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(redis.hasKey(trustKey(id, accepted.getResponse().getCookie("workflow_device"))))
                .isTrue();
    }

    @Test
    void failedSetupRollsBackPasswordTokenAuditAndDeviceTrust() throws Exception {
        UUID id = createInvitedUser("rollback@example.com");
        String token = InvitationTestConfiguration.tokens.get("rollback@example.com");
        Cookie device = new Cookie("workflow_device", deviceCookie.newValue());
        database.execute(
                "alter table password_setup_events add constraint test_setup_failure check (action <> 'INVITATION_DEVICE_TRUSTED')");
        try {
            write(
                            post("/api/auth/set-password").cookie(device),
                            Map.of("token", token, "password", "N7!mK4#qR9@x"),
                            null)
                    .andExpect(status().isInternalServerError());
        } finally {
            database.execute("alter table password_setup_events drop constraint test_setup_failure");
        }
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("DISABLED");
        assertThat(database.queryForObject(
                        "select consumed_at from user_invitations where user_id=?", java.time.OffsetDateTime.class, id))
                .isNull();
        assertThat(database.queryForObject(
                        "select count(*) from password_setup_events where user_id=?", Integer.class, id))
                .isZero();
        assertThat(redis.hasKey(trustKey(id, device))).isFalse();
        write(post("/api/auth/set-password").cookie(device), Map.of("token", token, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isNoContent());
    }

    @Test
    void concurrentPasswordSetupConsumesTheTokenOnlyOnce() throws Exception {
        UUID id = createInvitedUser("concurrent-setup@example.com");
        String token = InvitationTestConfiguration.tokens.get("concurrent-setup@example.com");
        Cookie first = new Cookie("workflow_device", deviceCookie.newValue());
        Cookie second = new Cookie("workflow_device", deviceCookie.newValue());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.Stream.of(first, second)
                    .map(device -> executor.submit(() -> {
                        start.await();
                        return write(
                                        post("/api/auth/set-password").cookie(device),
                                        Map.of("token", token, "password", "N7!mK4#qR9@x"),
                                        null)
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();
            start.countDown();
            assertThat(java.util.List.of(
                            attempts.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS),
                            attempts.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 410);
        }
        assertThat(java.util.List.of(redis.hasKey(trustKey(id, first)), redis.hasKey(trustKey(id, second))))
                .containsExactlyInAnyOrder(true, false);
        assertThat(database.queryForObject(
                        "select count(*) from password_setup_events where user_id=?", Integer.class, id))
                .isEqualTo(2);
    }

    private UUID createInvitedUser(String email) throws Exception {
        var created = write(post("/api/users"), creation(email), admin)
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
    }

    private String trustKey(UUID id, Cookie device) {
        return "auth:trusted-device:" + id + ":" + deviceCookie.deviceId(device.getValue());
    }

    @Test
    void passwordInvitationResendDoesNotStartTelegramBinding() throws Exception {
        var created = write(post("/api/users"), creation("separate@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String firstPassword = InvitationTestConfiguration.tokens.get("separate@example.com");
        write(post("/api/users/" + id + "/password-invitation"), Map.of(), admin)
                .andExpect(status().isNoContent());
        String secondPassword = InvitationTestConfiguration.tokens.get("separate@example.com");
        assertThat(secondPassword).isNotEqualTo(firstPassword);
        write(post("/api/users/" + id + "/telegram-invitation"), Map.of(), admin)
                .andExpect(status().isNotFound());
        assertThat(InvitationTestConfiguration.tokens.get("separate@example.com"))
                .isEqualTo(secondPassword);
    }

    @Test
    void administratorCanOnlyUnbindTelegramAndUserStartsReplacementBinding() throws Exception {
        var created = write(post("/api/users"), creation("rebind@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        LoginSession userSession = activateAndLogin("rebind@example.com");
        String firstToken = bindingToken(userSession.access());
        bindTelegram(firstToken, 12345);

        write(delete("/api/users/" + id + "/telegram"), Map.of(), admin).andExpect(status().isNoContent());
        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isNull();

        write(post("/api/users/" + id + "/telegram/link"), Map.of(), admin).andExpect(status().isNotFound());
        write(post("/api/users/" + id + "/telegram-invitation"), Map.of(), admin)
                .andExpect(status().isNotFound());
        Cookie renewedSession = loginFromDevice("rebind@example.com", userSession.device());
        String replacement = bindingToken(renewedSession);
        assertThat(replacement).isNotEqualTo(firstToken);
        assertThat(redis.hasKey("auth:telegram-binding:" + digest(firstToken))).isFalse();
        assertThat(redis.hasKey("auth:telegram-binding:" + digest(replacement))).isTrue();
        bindTelegram(replacement, 67890);
        assertThat(database.queryForList(
                        "select action from telegram_binding_events where user_id=? order by created_at, action",
                        String.class,
                        id))
                .containsExactlyInAnyOrder("BOUND", "UNBOUND", "REBOUND");
    }

    @Test
    void openingPasswordInvitationDoesNotTrustDeviceBeforePasswordSetup() throws Exception {
        var created = write(post("/api/users"), creation("pending@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String passwordToken = InvitationTestConfiguration.tokens.get("pending@example.com");
        var opened = write(post("/api/user-invitations/open"), Map.of("token", passwordToken), null)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json.readTree(opened.getResponse().getContentAsByteArray())
                        .get("deviceStatus")
                        .asText())
                .isEqualTo("PENDING_PASSWORD_SETUP");
        Cookie device = opened.getResponse().getCookie("workflow_device");
        assertThat(device).isNotNull();
        String deviceId = deviceCookie.deviceId(device.getValue());
        assertThat(redis.hasKey("auth:pending-device:" + id + ":" + deviceId)).isFalse();
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceId)).isFalse();
    }

    @Test
    void firstLoginUsesTrustedDeviceAndTelegramCanBeBoundAfterSignIn() throws Exception {
        var created = write(post("/api/users"), creation("binding@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String passwordToken = InvitationTestConfiguration.tokens.get("binding@example.com");
        var opened = write(post("/api/user-invitations/open"), Map.of("token", passwordToken), null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie device = opened.getResponse().getCookie("workflow_device");
        String deviceId = deviceCookie.deviceId(device.getValue());

        write(
                        post("/api/user-invitations/accept").cookie(device),
                        Map.of("token", passwordToken, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        assertThat(redis.hasKey("auth:pending-device:" + id + ":" + deviceId)).isFalse();
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceId)).isTrue();
        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isNull();

        var loggedIn = mvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "binding@example.com", "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = loggedIn.getResponse().getCookie(ACCESS);
        assertThat(access).isNotNull();
        var unboundDevice = mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("workflow_device", deviceCookie.newValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "binding@example.com", "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(json.readTree(unboundDevice.getResponse().getContentAsByteArray())
                        .get("status")
                        .asText())
                .isEqualTo("PENDING_EMAIL");
        assertThat(InvitationTestConfiguration.emailCodes.get("binding@example.com"))
                .matches("[0-9]{6}");
        assertThat(unboundDevice.getResponse().getCookie(ACCESS)).isNull();
        write(post("/api/auth/telegram/link"), Map.of(), null).andExpect(status().isUnauthorized());
        var link = write(post("/api/auth/telegram/link"), Map.of(), access)
                .andExpect(status().isOk())
                .andReturn();
        String token = telegramToken(json.readTree(link.getResponse().getContentAsByteArray())
                .get("link")
                .asText());
        bindTelegram(token, 12345);
        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isEqualTo(12345L);

        var otherDevice = mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("workflow_device", deviceCookie.newValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "binding@example.com", "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(json.readTree(otherDevice.getResponse().getContentAsByteArray())
                        .get("status")
                        .asText())
                .isEqualTo("PENDING_TELEGRAM");
        assertThat(otherDevice.getResponse().getCookie(ACCESS)).isNull();
    }

    @Test
    void telegramBindingRequiresConfirmationAndCannotBeReplayed() throws Exception {
        var created = write(post("/api/users"), creation("approval@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        LoginSession userSession = activateAndLogin("approval@example.com");
        String token = bindingToken(userSession.access());

        startTelegram(token, 12345);
        assertThat(InvitationTestConfiguration.botMessages.get(12345L))
                .contains("approval@example.com", "Подтвердите привязку Telegram");
        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isNull();

        decideTelegram(token, 12345, true);
        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isEqualTo(12345L);
        assertThat(database.queryForObject(
                        "select action from telegram_binding_events where user_id=?", String.class, id))
                .isEqualTo("BOUND");
        decideTelegram(token, 12345, true);
        assertThat(InvitationTestConfiguration.botMessages.get(12345L)).contains("Ссылка недействительна");
        assertThat(database.queryForObject(
                        "select count(*) from telegram_binding_events where user_id=?", Integer.class, id))
                .isOne();
    }

    @Test
    void rejectingAnIndependentTelegramLinkDoesNotBlockTheTrustedDevice() throws Exception {
        var created = write(post("/api/users"), creation("rejection@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String invitation = InvitationTestConfiguration.tokens.get("rejection@example.com");
        var opened = write(post("/api/user-invitations/open"), Map.of("token", invitation), null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie device = opened.getResponse().getCookie("workflow_device");
        String deviceId = deviceCookie.deviceId(device.getValue());
        write(
                        post("/api/user-invitations/accept").cookie(device),
                        Map.of("token", invitation, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        var login = mvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "rejection@example.com", "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isOk())
                .andReturn();
        var link = write(
                        post("/api/auth/telegram/link"),
                        Map.of(),
                        login.getResponse().getCookie(ACCESS))
                .andExpect(status().isOk())
                .andReturn();
        String token = telegramToken(json.readTree(link.getResponse().getContentAsByteArray())
                .get("link")
                .asText());

        startTelegram(token, 12345);
        assertThat(InvitationTestConfiguration.botMessages.get(12345L)).contains("rejection@example.com");
        decideTelegram(token, 12345, false);

        assertThat(database.queryForObject("select telegram_chat_id from users where id=?", Long.class, id))
                .isNull();
        assertThat(redis.hasKey("auth:blocked-device:" + id + ":" + deviceId)).isFalse();
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceId)).isTrue();
        assertThat(redis.hasKey("auth:telegram-binding:" + digest(token))).isFalse();
    }

    @Test
    void botExplainsMissingAndExpiredLinksAndRejectsAnOccupiedChat() throws Exception {
        mvc.perform(post("/api/auth/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message",
                                Map.of(
                                        "text", "/start",
                                        "chat", Map.of("id", 12345, "type", "private"),
                                        "from", Map.of("id", 12345))))))
                .andExpect(status().isNoContent());
        assertThat(InvitationTestConfiguration.botMessages.get(12345L)).contains("войдите в систему");

        startTelegram("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 12345);
        assertThat(InvitationTestConfiguration.botMessages.get(12345L)).contains("Запросите новую");

        write(post("/api/users"), creation("occupied@example.com"), admin).andExpect(status().isCreated());
        LoginSession occupiedSession = activateAndLogin("occupied@example.com");
        String first = bindingToken(occupiedSession.access());
        bindTelegram(first, 12345);
        write(post("/api/users"), creation("other@example.com"), admin).andExpect(status().isCreated());
        LoginSession otherSession = activateAndLogin("other@example.com");
        String second = bindingToken(otherSession.access());
        startTelegram(second, 12345);
        assertThat(InvitationTestConfiguration.botMessages.get(12345L))
                .contains("уже привязан к другой учётной записи");
        assertThat(database.queryForObject(
                        "select telegram_chat_id from users where email='other@example.com'", Long.class))
                .isNull();
    }

    @Test
    void passwordInvitationTrustsOnlyTheBrowserThatSetsThePassword() throws Exception {
        var created = write(post("/api/users"), creation("telegram-first@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String passwordToken = InvitationTestConfiguration.tokens.get("telegram-first@example.com");
        var opened = write(post("/api/user-invitations/open"), Map.of("token", passwordToken), null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie openedDevice = opened.getResponse().getCookie("workflow_device");
        Cookie acceptingDevice = new Cookie("workflow_device", deviceCookie.newValue());

        write(
                        post("/api/user-invitations/accept").cookie(acceptingDevice),
                        Map.of("token", passwordToken, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceCookie.deviceId(openedDevice.getValue())))
                .isFalse();
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceCookie.deviceId(acceptingDevice.getValue())))
                .isTrue();
    }

    @Test
    void passwordSetupDoesNotRequireTelegramBinding() throws Exception {
        var created = write(post("/api/users"), creation("email-first@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        String passwordToken = InvitationTestConfiguration.tokens.get("email-first@example.com");
        var opened = write(post("/api/user-invitations/open"), Map.of("token", passwordToken), null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie device = opened.getResponse().getCookie("workflow_device");
        write(
                        post("/api/user-invitations/accept").cookie(device),
                        Map.of("token", passwordToken, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        assertThat(database.queryForObject("select status from users where id=?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(redis.hasKey("auth:trusted-device:" + id + ":" + deviceCookie.deviceId(device.getValue())))
                .isTrue();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void userCreationSurvivesMailDeliveryFailureAndCanBeRetried(CapturedOutput output) throws Exception {
        InvitationTestConfiguration.failPassword = true;
        var created = write(post("/api/users"), creation("retry@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        assertThat(database.queryForObject("select count(*) from users where id=?", Integer.class, id))
                .isOne();
        assertThat(database.queryForObject("select count(*) from user_invitations where user_id=?", Integer.class, id))
                .isOne();
        assertThat(output.getAll()).contains("Failed to send password setup invitation for user " + id);
        assertThat(output.getAll()).doesNotContain("retry@example.com");

        InvitationTestConfiguration.failPassword = false;
        write(post("/api/users/" + id + "/password-invitation"), Map.of(), admin)
                .andExpect(status().isNoContent());
        assertThat(InvitationTestConfiguration.tokens).containsKey("retry@example.com");
    }

    @Test
    void invitationExpiresAfterConfiguredTtl() throws Exception {
        write(post("/api/users"), creation("expired@example.com"), admin).andExpect(status().isCreated());
        String token = InvitationTestConfiguration.tokens.get("expired@example.com");
        database.update(
                "update user_invitations set expires_at=now()-interval '1 second' where user_id=(select id from users where email='expired@example.com')");
        write(post("/api/user-invitations/accept"), Map.of("token", token, "password", "N7!mK4#qR9@x"), null)
                .andExpect(status().isGone());
    }

    @Test
    void validatesRegionRequirementFromRoleConfiguration() throws Exception {
        var ceo = new java.util.HashMap<String, Object>();
        ceo.put("email", "ceo@example.com");
        ceo.put("fullName", "CEO User");
        ceo.put("role", "CEO");
        write(post("/api/users"), ceo, admin).andExpect(status().isCreated());

        var manager = new java.util.HashMap<String, Object>(ceo);
        manager.put("email", "missing-region@example.com");
        manager.put("role", "MANAGER");
        write(post("/api/users"), manager, admin).andExpect(status().isBadRequest());

        mvc.perform(patch("/api/users/" + MANAGER_ID)
                        .cookie(admin, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionId\":null}"))
                .andExpect(status().isBadRequest());
        write(patch("/api/users/" + MANAGER_ID), Map.of("role", "CEO"), admin).andExpect(status().isOk());
        mvc.perform(patch("/api/users/" + MANAGER_ID)
                        .cookie(admin, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionId\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateWithConflict() throws Exception {
        write(post("/api/users"), creation(" MANAGER@EXAMPLE.COM "), admin).andExpect(status().isConflict());
        assertThat(database.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(2);
        assertThat(database.queryForObject("select count(*) from user_access_history", Integer.class))
                .isZero();
    }

    @Test
    void concurrentDuplicateCreationCommitsOnlyOneUserAndAuditEntry() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var call = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return write(post("/api/users"), creation("race@example.com"), admin)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            assertThat(java.util.List.of(
                            first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                            second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(database.queryForObject("select count(*) from user_access_history", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void listsWithCombinedFiltersAndBoundedPagination() throws Exception {
        insertUser(UUID.randomUUID(), "other@example.com", "MANAGER", OTHER_REGION, "DISABLED");
        for (String filter : java.util.List.of(
                "role=MANAGER&status=ACTIVE",
                "role=MANAGER&regionId=" + RESOURCE_REGION,
                "regionId=" + RESOURCE_REGION + "&status=ACTIVE&role=MANAGER")) {
            var result = mvc.perform(get("/api/users?" + filter).cookie(admin))
                    .andExpect(status().isOk())
                    .andReturn();
            var page = json.readTree(result.getResponse().getContentAsByteArray());
            assertThat(page.get("totalElements").asInt()).isEqualTo(1);
            assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(MANAGER_ID.toString());
            assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        }
        var result = mvc.perform(get("/api/users?size=1&page=1").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsByteArray())
                        .get("content")
                        .size())
                .isEqualTo(1);
    }

    @Test
    void listAndDetailShowPasswordAndTelegramBindingState() throws Exception {
        var created = write(post("/api/users"), creation("state@example.com"), admin)
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray())
                .get("id")
                .asText());
        assertUserFlags(id, false, false);

        LoginSession session = activateAndLogin("state@example.com");
        assertUserFlags(id, true, false);

        bindTelegram(bindingToken(session.access()), 12345);
        assertUserFlags(id, true, true);

        write(delete("/api/users/" + id + "/telegram"), Map.of(), admin).andExpect(status().isNoContent());
        assertUserFlags(id, true, false);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"page=-1", "size=0", "size=101", "role=UNKNOWN", "status=wrong", "regionId=wrong", "page=abc"})
    void rejectsInvalidFilters(String query) throws Exception {
        mvc.perform(get("/api/users?" + query).cookie(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void patchesOnlyRequestedFieldsAndKeepsObjectReferences() throws Exception {
        UUID objectId = UUID.randomUUID();
        database.update("insert into test_object_links values(?,?,?)", objectId, MANAGER_ID, RESOURCE_REGION);
        write(patch("/api/users/" + MANAGER_ID), Map.of("regionId", OTHER_REGION), admin)
                .andExpect(status().isOk());
        assertThat(database.queryForObject(
                        "select (select region_id from user_regions where user_id=? order by region_id limit 1)",
                        UUID.class,
                        MANAGER_ID))
                .isEqualTo(OTHER_REGION);
        assertThat(database.queryForObject("select status from users where id=?", String.class, MANAGER_ID))
                .isEqualTo("ACTIVE");
        assertThat(database.queryForObject("select manager_id from test_object_links where id=?", UUID.class, objectId))
                .isEqualTo(MANAGER_ID);
        assertThat(database.queryForObject("select region_id from test_object_links where id=?", UUID.class, objectId))
                .isEqualTo(RESOURCE_REGION);
        assertThat(database.queryForObject(
                        "select old_region_id from user_access_history where user_id=?", UUID.class, MANAGER_ID))
                .isEqualTo(RESOURCE_REGION);
        write(patch("/api/users/" + MANAGER_ID), Map.of("role", "REGIONAL_MANAGER", "status", "DISABLED"), admin)
                .andExpect(status().isOk());
        assertThat(database.queryForObject("select count(*) from users where id=?", Integer.class, MANAGER_ID))
                .isEqualTo(1);
        assertThat(database.queryForObject("select count(*) from test_object_links", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsSelfDeactivationAndSelfDemotion() throws Exception {
        write(patch("/api/users/" + USER_ID), Map.of("status", "DISABLED"), admin)
                .andExpect(status().isConflict());
        write(patch("/api/users/" + USER_ID), Map.of("role", "MANAGER"), admin).andExpect(status().isConflict());
        assertThat(database.queryForObject("select count(*) from user_access_history", Integer.class))
                .isZero();
    }

    @Test
    void rejectsStaleVersionAndDoesNotAuditNoop() throws Exception {
        write(patch("/api/users/" + MANAGER_ID), Map.of("status", "ACTIVE"), admin)
                .andExpect(status().isOk());
        assertThat(database.queryForObject("select count(*) from user_access_history", Integer.class))
                .isZero();
        write(patch("/api/users/" + MANAGER_ID), Map.of("status", "DISABLED", "version", 100), admin)
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingFieldsUnknownReferencesAndEmptyPatch() throws Exception {
        for (String field : java.util.List.of("email", "fullName", "role", "regionId")) {
            var request = new java.util.HashMap<String, Object>(creation("new@example.com"));
            request.remove(field);
            write(post("/api/users"), request, admin).andExpect(status().isBadRequest());
        }
        var bad = new java.util.HashMap<String, Object>(creation("not-an-email"));
        write(post("/api/users"), bad, admin).andExpect(status().isBadRequest());
        bad.put("email", "new@example.com");
        bad.put("regionId", UUID.randomUUID());
        write(post("/api/users"), bad, admin).andExpect(status().isBadRequest());
        write(patch("/api/users/" + MANAGER_ID), Map.of(), admin).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/" + MANAGER_ID)
                        .cookie(admin, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest());
        write(patch("/api/users/" + UUID.randomUUID()), Map.of("status", "DISABLED"), admin)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER"})
    void rejectsEveryNonAdministratorRole(String role) throws Exception {
        database.update("update users set role_id=(select id from roles where code=?) where id=?", role, MANAGER_ID);
        Cookie user = login("manager@example.com");
        mvc.perform(get("/api/users").cookie(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + USER_ID).cookie(user)).andExpect(status().isForbidden());
        write(post("/api/users"), creation("new@example.com"), user).andExpect(status().isForbidden());
        write(patch("/api/users/" + USER_ID), Map.of("status", "DISABLED"), user)
                .andExpect(status().isForbidden());
        write(post("/api/users/" + USER_ID + "/password-invitation"), Map.of(), user)
                .andExpect(status().isForbidden());
        write(delete("/api/users/" + USER_ID + "/telegram"), Map.of(), user).andExpect(status().isForbidden());
    }

    @Test
    void checksCurrentRoleAndStatusInsteadOfStaleJwtClaims() throws Exception {
        database.update("update users set role_id=(select id from roles where code='MANAGER') where id=?", USER_ID);
        mvc.perform(get("/api/users").cookie(admin)).andExpect(status().isForbidden());
        database.update(
                "update users set role_id=(select id from roles where code='SUPERADMIN'), status='DISABLED' where id=?",
                USER_ID);
        mvc.perform(get("/api/users").cookie(admin)).andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthenticationAndCsrfAndDoesNotExposeDelete() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").cookie(new Cookie(ACCESS, "invalid"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(creation("new@example.com"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + MANAGER_ID).cookie(admin, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsUnsupportedFieldsInsteadOfSilentlyIgnoringCredentials() throws Exception {
        var request = new java.util.HashMap<String, Object>(creation("new@example.com"));
        request.put("password", "must-not-be-accepted");
        write(post("/api/users"), request, admin).andExpect(status().isBadRequest());
        write(patch("/api/users/" + MANAGER_ID), Map.of("status", "DISABLED", "email", "changed@example.com"), admin)
                .andExpect(status().isBadRequest());
        write(patch("/api/users/" + MANAGER_ID), Map.of("telegramChatId", 12345), admin)
                .andExpect(status().isBadRequest());
        assertThat(database.queryForObject("select count(*) from user_access_history", Integer.class))
                .isZero();
    }

    @Test
    void deactivationRejectsLoginAndRefreshAndReactivationPreservesIdentity() throws Exception {
        var session = trustedLogin("manager@example.com", "test-password")
                .andExpect(status().isOk())
                .andReturn();
        var refresh = session.getResponse().getCookie("workflow_refresh_token");
        write(patch("/api/users/" + MANAGER_ID), Map.of("status", "DISABLED"), admin)
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "manager@example.com", "password", "test-password"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(refresh, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
        write(patch("/api/users/" + MANAGER_ID), Map.of("status", "ACTIVE"), admin)
                .andExpect(status().isOk());
        assertThat(login("manager@example.com")).isNotNull();
        assertThat(database.queryForObject("select id from users where email='manager@example.com'", UUID.class))
                .isEqualTo(MANAGER_ID);
    }

    @Test
    void auditFailureRollsBackTheUserChange() throws Exception {
        database.execute(
                "alter table user_access_history add constraint test_reject_audit check (action <> 'CREATED')");
        try {
            write(post("/api/users"), creation("rollback@example.com"), admin)
                    .andExpect(status().isInternalServerError());
            assertThat(database.queryForObject(
                            "select count(*) from users where email='rollback@example.com'", Integer.class))
                    .isZero();
        } finally {
            database.execute("alter table user_access_history drop constraint test_reject_audit");
        }
    }

    private Map<String, Object> creation(String email) {
        return Map.of("email", email, "fullName", " New User ", "role", "MANAGER", "regionId", RESOURCE_REGION);
    }

    private void assertUserFlags(UUID id, boolean passwordSet, boolean telegramLinked) throws Exception {
        var detail = mvc.perform(get("/api/users/" + id).cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        var user = json.readTree(detail.getResponse().getContentAsByteArray());
        assertThat(user.get("passwordSet").asBoolean()).isEqualTo(passwordSet);
        assertThat(user.get("telegramLinked").asBoolean()).isEqualTo(telegramLinked);

        var result = mvc.perform(get("/api/users?role=MANAGER").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        var page = json.readTree(result.getResponse().getContentAsByteArray());
        boolean found = false;
        for (var item : page.get("content")) {
            if (id.toString().equals(item.get("id").asText())) {
                assertThat(item.get("passwordSet").asBoolean()).isEqualTo(passwordSet);
                assertThat(item.get("telegramLinked").asBoolean()).isEqualTo(telegramLinked);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    private ResultActions write(MockHttpServletRequestBuilder request, Object body, Cookie user) throws Exception {
        request.cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken);
        if (user != null) {
            request.cookie(user);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private Cookie login(String email) throws Exception {
        return loginWithPassword(email, "test-password");
    }

    private Cookie loginWithPassword(String email, String password) throws Exception {
        return trustedLogin(email, password)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(ACCESS);
    }

    private LoginSession activateAndLogin(String email) throws Exception {
        String token = InvitationTestConfiguration.tokens.get(email);
        var opened = write(post("/api/user-invitations/open"), Map.of("token", token), null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie device = opened.getResponse().getCookie("workflow_device");
        assertThat(device).isNotNull();
        write(
                        post("/api/user-invitations/accept").cookie(device),
                        Map.of("token", token, "password", "N7!mK4#qR9@x"),
                        null)
                .andExpect(status().isNoContent());
        return new LoginSession(loginFromDevice(email, device), device);
    }

    private Cookie loginFromDevice(String email, Cookie device) throws Exception {
        var result = mvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "N7!mK4#qR9@x"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = result.getResponse().getCookie(ACCESS);
        assertThat(access).isNotNull();
        return access;
    }

    private String bindingToken(Cookie access) throws Exception {
        var result = write(post("/api/auth/telegram/link"), Map.of(), access)
                .andExpect(status().isOk())
                .andReturn();
        return telegramToken(json.readTree(result.getResponse().getContentAsByteArray())
                .get("link")
                .asText());
    }

    private record LoginSession(Cookie access, Cookie device) {}

    private ResultActions trustedLogin(String email, String password) throws Exception {
        UUID userId = database.queryForObject("select id from users where email=?", UUID.class, email);
        Long chatId = database.queryForObject("select telegram_chat_id from users where id=?", Long.class, userId);
        if (chatId == null) {
            Long nextChatId =
                    database.queryForObject("select coalesce(max(telegram_chat_id), 0) + 1 from users", Long.class);
            database.update("update users set telegram_chat_id=? where id=?", nextChatId, userId);
        }
        redis.opsForValue()
                .set(
                        "auth:trusted-device:" + userId + ":" + deviceCookie.deviceId(DEVICE.getValue()),
                        "1",
                        Duration.ofDays(30));
        return mvc.perform(post("/api/auth/login")
                .cookie(DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void insertUser(UUID id, String email, String role, UUID region, String status) {
        database.update(
                "insert into users(id,email,password_hash,full_name,role_id,status) "
                        + "values(?,?,?,'Test User',(select id from roles where code=?),?)",
                id,
                email,
                passwords.encode("test-password"),
                role,
                status);
        UserRegionTestData.assign(database, id, region);
    }

    private void bindTelegram(String token, long chatId) throws Exception {
        startTelegram(token, chatId);
        decideTelegram(token, chatId, true);
    }

    private void startTelegram(String token, long chatId) throws Exception {
        mvc.perform(post("/api/auth/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message",
                                Map.of(
                                        "text", "/start " + token,
                                        "chat", Map.of("id", chatId, "type", "private"),
                                        "from", Map.of("id", chatId))))))
                .andExpect(status().isNoContent());
    }

    private void decideTelegram(String token, long chatId, boolean approve) throws Exception {
        mvc.perform(post("/api/auth/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "callback_query",
                                Map.of(
                                        "id",
                                        "binding-approval",
                                        "data",
                                        "bind:" + (approve ? "approve:" : "reject:") + token,
                                        "from",
                                        Map.of("id", chatId),
                                        "message",
                                        Map.of("chat", Map.of("id", chatId, "type", "private")))))))
                .andExpect(status().isNoContent());
    }

    private static String telegramToken(String link) {
        return link.substring(link.indexOf("start=") + 6);
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class InvitationTestConfiguration {
        static final java.util.concurrent.ConcurrentMap<String, String> tokens =
                new java.util.concurrent.ConcurrentHashMap<>();
        static final java.util.concurrent.ConcurrentMap<Long, String> botMessages =
                new java.util.concurrent.ConcurrentHashMap<>();
        static final java.util.concurrent.ConcurrentMap<String, String> emailCodes =
                new java.util.concurrent.ConcurrentHashMap<>();
        static final java.util.concurrent.atomic.AtomicInteger passwordDeliveries =
                new java.util.concurrent.atomic.AtomicInteger();
        static volatile boolean failPassword;

        @Bean
        @Primary
        UserInvitationSender invitationSender() {
            return (email, fullName, token) -> {
                if (failPassword) {
                    throw new IllegalStateException("SMTP is unavailable");
                }
                passwordDeliveries.incrementAndGet();
                tokens.put(email, token);
            };
        }

        @Bean
        @Primary
        EmailChallengeSender emailChallengeSender() {
            return new EmailChallengeSender() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public void send(String email, String code) {
                    emailCodes.put(email, code);
                }
            };
        }

        @Bean
        @Primary
        TelegramChallengeSender telegramChallengeSender() {
            return new TelegramChallengeSender() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public void send(long chatId, dev.workflowcrm.auth.domain.LoginChallenge challenge) {
                    botMessages.put(chatId, "Login confirmation");
                }
            };
        }

        @Bean
        @Primary
        TelegramBotGateway telegramBotGateway() {
            return new TelegramBotGateway() {
                @Override
                public void sendMessage(long chatId, String text, java.util.List<Button> buttons) {
                    botMessages.put(chatId, text);
                }

                @Override
                public void answerCallback(String callbackId, String text) {}
            };
        }
    }
}
