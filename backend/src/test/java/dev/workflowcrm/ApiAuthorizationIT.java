package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.api.AccessScope;
import dev.workflowcrm.auth.api.ResourceAccessGuard;
import dev.workflowcrm.auth.api.ResourceScope;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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
            "app.security.jwt.issuer=workflow-access-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.jwt.secure=true"
        })
@Import(ApiAuthorizationIT.ProtectedEndpointConfiguration.class)
class ApiAuthorizationIT {

    private static final String ACCESS = "workflow_access_token";
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
    private ObjectMapper json;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private DeviceCookie deviceCookie;

    private static final Cookie DEVICE = new Cookie(
            "workflow_device", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));

    @BeforeEach
    void setUp() {
        database.update("delete from users");
        database.update(
                "insert into users (id, email, password_hash, full_name, role_id, status) "
                        + "values (?, 'access@example.com', ?, 'Access User', "
                        + "(select id from roles where code = 'MANAGER'), 'ACTIVE')",
                USER_ID,
                passwords.encode("test-password"));
        try (var connection =
                java.util.Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushDb();
        }
        database.update("update users set telegram_chat_id = 780 where id = ?", USER_ID);
        redis.opsForValue()
                .set(
                        "auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(DEVICE.getValue()),
                        "1",
                        Duration.ofDays(30));
    }

    @Test
    void apiEnforcesCurrentRoleAndRegionAndReturnsGenericForbidden() throws Exception {
        database.update(
                "insert into regions (id, code, name) values (?, 'ACCESS_TEST', 'Access Test') on conflict (id) do nothing",
                RESOURCE_REGION);
        database.update(
                "update users set  role_id = (select id from roles where code = 'REGIONAL_MANAGER') where id = ?",
                USER_ID);
        UserRegionTestData.replace(database, USER_ID, RESOURCE_REGION);
        Cookie access = login().getResponse().getCookie(ACCESS);
        mvc.perform(get("/api/access-test/region").cookie(access)).andExpect(status().isOk());
        UserRegionTestData.replace(database, USER_ID);
        MvcResult denied = mvc.perform(get("/api/access-test/region").cookie(access))
                .andExpect(status().isForbidden())
                .andReturn();
        var body = json.readTree(denied.getResponse().getContentAsByteArray());
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("message").asText()).isEqualTo("Access denied");
        assertThat(denied.getResponse().getContentAsString())
                .doesNotContain(RESOURCE_REGION.toString(), "confidential");
        database.update(
                "update users set  role_id = (select id from roles where code = 'MANAGER') where id = ?", USER_ID);
        UserRegionTestData.replace(database, USER_ID, RESOURCE_REGION);
        mvc.perform(get("/api/access-test/region").cookie(access)).andExpect(status().isForbidden());
    }

    @Test
    void protectedApiRejectsMissingInvalidAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/access-test/owner")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/access-test/owner").cookie(new Cookie(ACCESS, "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/access-test/owner").cookie(expiredAccess())).andExpect(status().isUnauthorized());
        Cookie access = login().getResponse().getCookie(ACCESS);
        mvc.perform(get("/api/access-test/owner").cookie(access)).andExpect(status().isOk());
        mvc.perform(get("/api/access-test/another-owner").cookie(access)).andExpect(status().isForbidden());
        database.update("update users set status = 'DISABLED' where id = ?", USER_ID);
        mvc.perform(get("/api/access-test/owner").cookie(access)).andExpect(status().isForbidden());
    }

    @Test
    void onlyExplicitPublicEndpointsBypassAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/liveness").cookie(new Cookie(ACCESS, "invalid")))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf").cookie(new Cookie(ACCESS, "invalid"))).andExpect(status().isOk());
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .cookie(DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"access@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie expiredAccess() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("workflow-access-test")
                .subject(USER_ID.toString())
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .id(UUID.randomUUID().toString())
                .build();
        return new Cookie(
                ACCESS,
                encoder.encode(JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue());
    }

    @TestConfiguration
    static class ProtectedEndpointConfiguration {
        @RestController
        static class ProtectedController {
            private final ResourceAccessGuard access;

            ProtectedController(ResourceAccessGuard access) {
                this.access = access;
            }

            @GetMapping("/api/access-test/region")
            Map<String, String> region() {
                access.requireAccess(
                        new ResourceScope(USER_ID, RESOURCE_REGION), Set.of("REGIONAL_MANAGER"), AccessScope.REGION);
                return Map.of("value", "confidential");
            }

            @GetMapping("/api/access-test/owner")
            Map<String, String> owner() {
                access.requireAccess(new ResourceScope(USER_ID, RESOURCE_REGION), Set.of("MANAGER"), AccessScope.OWNER);
                return Map.of("status", "ok");
            }

            @GetMapping("/api/access-test/another-owner")
            Map<String, String> anotherOwner() {
                access.requireAccess(
                        new ResourceScope(UUID.fromString("00000000-0000-0000-0003-000000000003"), RESOURCE_REGION),
                        Set.of("MANAGER"),
                        AccessScope.OWNER);
                return Map.of("status", "ok");
            }
        }
    }
}
