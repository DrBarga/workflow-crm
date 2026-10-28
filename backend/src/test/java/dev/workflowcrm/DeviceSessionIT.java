package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
            "app.security.jwt.issuer=workflow-device-session-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.refresh-cookie-name=workflow_refresh_token",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.jwt.refresh-token-ttl=7d",
            "app.security.jwt.secure=true"
        })
class DeviceSessionIT {

    private static final String ACCESS = "workflow_access_token";
    private static final String REFRESH = "workflow_refresh_token";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0005-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0005-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0005-000000000003");
    private static final Cookie DEVICE_A = deviceCookie((byte) 1);
    private static final Cookie DEVICE_B = deviceCookie((byte) 2);
    private static final Cookie DEVICE_C = deviceCookie((byte) 3);

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
    private ObjectMapper json;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private DeviceCookie deviceIdentity;

    @BeforeEach
    void setUp() {
        database.update("delete from users");
        createUser(USER_ID, "user@example.com", "MANAGER");
        createUser(OTHER_USER_ID, "other@example.com", "MANAGER");
        createUser(ADMIN_ID, "admin@example.com", "SUPERADMIN");
        try (var connection =
                java.util.Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void listsOnlyOwnActiveDeviceSessionsWithLastLoginAndCurrentMarker() throws Exception {
        MvcResult older = login(USER_ID, "user@example.com", DEVICE_A, "Browser A");
        String olderId = sessionId(older);
        redis.opsForHash()
                .put(
                        "auth:refresh-session:" + olderId,
                        "createdAt",
                        Long.toString(Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()));
        MvcResult newer = login(USER_ID, "user@example.com", DEVICE_B, "Browser B");
        String newerId = sessionId(newer);
        login(OTHER_USER_ID, "other@example.com", DEVICE_C, "Other browser");

        mvc.perform(get("/api/auth/devices").cookie(newer.getResponse().getCookie(ACCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newerId))
                .andExpect(jsonPath("$[0].userAgent").value("Browser B"))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].deviceId").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(olderId))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    void userRevokesOnlyOwnedSessionAndCurrentRevocationClearsCookies() throws Exception {
        MvcResult first = login(USER_ID, "user@example.com", DEVICE_A, "Browser A");
        MvcResult current = login(USER_ID, "user@example.com", DEVICE_B, "Browser B");
        MvcResult other = login(OTHER_USER_ID, "other@example.com", DEVICE_C, "Other browser");
        Csrf csrf = csrf();

        revokeOwn(sessionId(first), current.getResponse().getCookie(ACCESS), csrf)
                .andExpect(status().isNoContent());
        me(first).andExpect(status().isUnauthorized());
        me(current).andExpect(status().isOk());

        revokeOwn(sessionId(other), current.getResponse().getCookie(ACCESS), csrf)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found"));
        me(other).andExpect(status().isOk());

        MvcResult revoked = revokeOwn(sessionId(current), current.getResponse().getCookie(ACCESS), csrf)
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(revoked.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie).contains(ACCESS + "=", "Max-Age=0"))
                .anySatisfy(cookie -> assertThat(cookie).contains(REFRESH + "=", "Max-Age=0"));
        me(current).andExpect(status().isUnauthorized());
    }

    @Test
    void superadminRevokesAnyUsersSingleOrAllSessionsUsingCurrentDatabaseRole() throws Exception {
        MvcResult administrator = login(ADMIN_ID, "admin@example.com", DEVICE_A, "Admin browser");
        MvcResult first = login(USER_ID, "user@example.com", DEVICE_B, "Browser B");
        MvcResult second = login(USER_ID, "user@example.com", DEVICE_C, "Browser C");
        Csrf csrf = csrf();

        mvc.perform(delete("/api/users/{userId}/devices/{sessionId}", USER_ID, sessionId(first))
                        .cookie(administrator.getResponse().getCookie(ACCESS), csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());

        mvc.perform(delete("/api/users/{userId}/devices", USER_ID)
                        .cookie(administrator.getResponse().getCookie(ACCESS), csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent());
        me(second).andExpect(status().isUnauthorized());
        me(administrator).andExpect(status().isOk());

        MvcResult replacement = login(USER_ID, "user@example.com", DEVICE_B, "Browser B");
        database.update(
                "update users set role_id = (select id from roles where code = 'MANAGER') where id = ?", ADMIN_ID);
        mvc.perform(delete("/api/users/{userId}/devices/{sessionId}", USER_ID, sessionId(replacement))
                        .cookie(administrator.getResponse().getCookie(ACCESS), csrf.cookie())
                        .header(csrf.header(), csrf.token()))
                .andExpect(status().isForbidden());
        me(replacement).andExpect(status().isOk());
    }

    @Test
    void endpointsRequireAuthenticationAndCsrfForChanges() throws Exception {
        mvc.perform(get("/api/auth/devices")).andExpect(status().isUnauthorized());
        MvcResult login = login(USER_ID, "user@example.com", DEVICE_A, "Browser A");
        mvc.perform(delete("/api/auth/devices/{sessionId}", sessionId(login))
                        .cookie(login.getResponse().getCookie(ACCESS)))
                .andExpect(status().isForbidden());
        me(login).andExpect(status().isOk());
    }

    private void createUser(UUID id, String email, String role) {
        database.update(
                "insert into users (id, email, password_hash, full_name, role_id, status, telegram_chat_id) "
                        + "values (?, ?, ?, 'Device User', (select id from roles where code = ?), 'ACTIVE', ?)",
                id,
                email,
                passwords.encode("test-password"),
                role,
                id.getLeastSignificantBits());
    }

    private MvcResult login(UUID userId, String email, Cookie device, String userAgent) throws Exception {
        trust(userId, device);
        return mvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void trust(UUID userId, Cookie device) {
        redis.opsForValue()
                .set(
                        "auth:trusted-device:" + userId + ":" + deviceIdentity.deviceId(device.getValue()),
                        "1",
                        Duration.ofDays(30));
    }

    private ResultActions revokeOwn(String sessionId, Cookie access, Csrf csrf) throws Exception {
        return mvc.perform(delete("/api/auth/devices/{sessionId}", sessionId)
                .cookie(access, csrf.cookie())
                .header(csrf.header(), csrf.token()));
    }

    private ResultActions me(MvcResult login) throws Exception {
        return mvc.perform(get("/api/auth/me").cookie(login.getResponse().getCookie(ACCESS)));
    }

    private String sessionId(MvcResult login) {
        return decoder.decode(login.getResponse().getCookie(ACCESS).getValue()).getClaimAsString("sid");
    }

    private Csrf csrf() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        return new Csrf(
                result.getResponse().getCookie("XSRF-TOKEN"),
                body.get("headerName").asText(),
                body.get("token").asText());
    }

    private static Cookie deviceCookie(byte value) {
        return new Cookie(
                "workflow_device", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {
                    value, value, value, value, value, value, value, value,
                    value, value, value, value, value, value, value, value,
                    value, value, value, value, value, value, value, value,
                    value, value, value, value, value, value, value, value
                }));
    }

    private record Csrf(Cookie cookie, String header, String token) {}
}
