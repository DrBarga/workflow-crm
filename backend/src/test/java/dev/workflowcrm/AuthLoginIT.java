package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.EmailChallengeSender;
import dev.workflowcrm.auth.domain.LoginChallenge;
import dev.workflowcrm.auth.domain.TelegramChallengeSender;
import dev.workflowcrm.auth.domain.TokenRevocationStore;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(
        printOnlyOnFailure = false,
        print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test-access-key",
            "app.minio.secret-key=test-secret-key",
            "app.security.jwt.secret=test-jwt-signing-secret-with-32-bytes",
            "app.security.jwt.issuer=workflow-crm-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.jwt.secure=true",
            "app.security.login.max-attempts=5",
            "app.security.login.lock-duration=15m",
            "app.security.challenge.ttl=5m",
            "app.security.challenge.max-email-attempts=5",
            "app.security.telegram.bot-username=workflow_test_bot",
            "app.security.telegram.bot-token=test-bot-token",
            "app.security.telegram.webhook-secret=test-webhook-secret"
        })
@Import(AuthLoginIT.ProtectedEndpointConfiguration.class)
class AuthLoginIT {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0002-000000000001");
    private static final UUID REGION_ID = UUID.fromString("00000000-0000-0000-0002-000000000002");
    private static final String EMAIL = "manager@example.com";
    private static final String PASSWORD = "correct-password";
    private static final String COOKIE_NAME = "workflow_access_token";
    private static final Cookie DEVICE = new Cookie(
            "workflow_device", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("workflow_crm")
            .withUsername("workflow_app")
            .withPassword("workflow_test_password");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenRevocationStore revocationStore;

    @Autowired
    private DeviceCookie deviceCookie;

    @Autowired
    private FakeEmailSender fakeEmail;

    @Autowired
    private FakeTelegramSender fakeTelegram;

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from telegram_binding_events");
        jdbcTemplate.update("delete from users");
        jdbcTemplate.update("delete from regions");
        flushRedis();
        fakeEmail.reset();
        fakeTelegram.reset();
        insertRegion();
        insertUser("ACTIVE");
        jdbcTemplate.update("update users set telegram_chat_id = 777 where id = ?", USER_ID);
        redisTemplate
                .opsForValue()
                .set(
                        "auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(DEVICE.getValue()),
                        "1",
                        Duration.ofDays(30));
    }

    @Test
    void returnsProfileAndSecureCookieForValidCredentials() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(COOKIE_NAME + "=")
                .contains("Path=/")
                .contains("Max-Age=900")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        Map<String, Object> response =
                objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() {});
        assertThat(response.keySet()).isEqualTo(Set.of("id", "role", "region", "name"));
        assertThat(response.get("id")).isEqualTo(USER_ID.toString());
        assertThat(response.get("role")).isEqualTo("MANAGER");
        assertThat(response.get("region")).isEqualTo(REGION_ID.toString());
        assertThat(response.get("name")).isEqualTo("Test Manager");

        mockMvc.perform(get("/api/test").cookie(result.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isOk());
    }

    @Test
    void unknownDeviceGetsNoTokensUntilTelegramApproves() throws Exception {
        MvcResult pending = rawLogin().andExpect(status().isAccepted()).andReturn();
        assertThat(pending.getResponse().getCookie(COOKIE_NAME)).isNull();
        assertThat(pending.getResponse().getCookie("workflow_refresh_token")).isNull();
        var body = objectMapper.readTree(pending.getResponse().getContentAsByteArray());
        String id = body.get("challengeId").asText();
        assertThat(body.get("status").asText()).isEqualTo("PENDING_TELEGRAM");
        assertThat(fakeTelegram.challenge.id()).isEqualTo(id);
        Cookie device = pending.getResponse().getCookie("workflow_device");
        CsrfCredentials csrf = csrfCredentials();
        mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());

        telegramUpdate(Map.of("callback_query", Map.of("data", "auth:approve:" + id, "from", Map.of("id", 777))))
                .andExpect(status().isNoContent());
        MvcResult approved = mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(approved.getResponse().getCookie(COOKIE_NAME)).isNotNull();
        assertThat(approved.getResponse().getCookie("workflow_refresh_token")).isNotNull();
        assertThat(redisTemplate.hasKey(
                        "auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(device.getValue())))
                .isTrue();
        mockMvc.perform(get("/api/test").cookie(approved.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isOk());
    }

    @Test
    void unboundUserCompletesLoginWithEmailConfirmation() throws Exception {
        jdbcTemplate.update("update users set telegram_chat_id = null where id = ?", USER_ID);
        MvcResult pending = rawLogin().andExpect(status().isAccepted()).andReturn();
        assertThat(pending.getResponse().getCookie(COOKIE_NAME)).isNull();
        assertThat(pending.getResponse().getCookie("workflow_refresh_token")).isNull();
        var body = objectMapper.readTree(pending.getResponse().getContentAsByteArray());
        String id = body.get("challengeId").asText();
        assertThat(body.get("status").asText()).isEqualTo("PENDING_EMAIL");
        assertThat(fakeTelegram.challenge).isNull();
        assertThat(fakeEmail.code).matches("[0-9]{6}");
        assertThat(redisTemplate.keys("auth:login-challenge:*"))
                .containsExactlyInAnyOrder("auth:login-challenge:" + id, "auth:login-challenge:" + id + ":resend");
        Cookie device = pending.getResponse().getCookie("workflow_device");
        CsrfCredentials csrf = csrfCredentials();
        MvcResult confirmed = mockMvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", fakeEmail.code))))
                .andExpect(status().isOk())
                .andReturn();
        var confirmation = objectMapper.readTree(confirmed.getResponse().getContentAsByteArray());
        assertThat(confirmation.get("status").asText()).isEqualTo("APPROVED");
        assertThat(confirmation.get("bindingLink").asText()).isEmpty();
        MvcResult completed = mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(completed.getResponse().getCookie(COOKIE_NAME)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select telegram_chat_id from users where id = ?", Long.class, USER_ID))
                .isNull();
    }

    @Test
    void unavailableTelegramFallsBackToOneTimeEmailCodeWithChallengeTtl() throws Exception {
        fakeTelegram.operational = false;

        MvcResult pending = rawLogin()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andReturn();
        assertThat(pending.getResponse().getCookie(COOKIE_NAME)).isNull();
        assertThat(pending.getResponse().getCookie("workflow_refresh_token")).isNull();
        assertThat(fakeTelegram.challenge).isNull();
        assertThat(fakeEmail.code).matches("[0-9]{6}");

        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        String challengeKey = "auth:login-challenge:" + id;
        assertThat(redisTemplate.getExpire(challengeKey)).isBetween(1L, 300L);

        Cookie device = pending.getResponse().getCookie("workflow_device");
        CsrfCredentials csrf = csrfCredentials();
        mockMvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", fakeEmail.code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(redisTemplate.opsForHash().hasKey(challengeKey, "codeDigest"))
                .isFalse();

        mockMvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", fakeEmail.code))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredEmailCodeCannotApproveDevice() throws Exception {
        fakeTelegram.operational = false;
        MvcResult pending = rawLogin()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andReturn();
        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        Cookie device = pending.getResponse().getCookie("workflow_device");
        CsrfCredentials csrf = csrfCredentials();

        redisTemplate.expire("auth:login-challenge:" + id, Duration.ofMillis(1));
        Thread.sleep(20);

        mockMvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", fakeEmail.code))))
                .andExpect(status().isGone());
        mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
        assertThat(redisTemplate.hasKey(
                        "auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(device.getValue())))
                .isFalse();
    }

    @Test
    void telegramDeliveryFailureFallsBackToEmail() throws Exception {
        fakeTelegram.failDelivery = true;

        MvcResult pending = rawLogin()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andReturn();
        String emailChallengeId = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();

        assertThat(fakeTelegram.challenge).isNotNull();
        assertThat(fakeTelegram.challenge.id()).isNotEqualTo(emailChallengeId);
        assertThat(redisTemplate.opsForHash().get("auth:login-challenge:" + fakeTelegram.challenge.id(), "status"))
                .isEqualTo("REJECTED");
        assertThat(fakeEmail.code).matches("[0-9]{6}");
    }

    @Test
    void linkedUserCannotForceEmailWhenTelegramWorks() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", EMAIL, "password", PASSWORD, "confirmationMethod", "EMAIL"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_TELEGRAM"));

        assertThat(fakeTelegram.challenge).isNotNull();
        assertThat(fakeEmail.code).isNull();
    }

    @Test
    void unavailableEmailFailsClosedWithoutCreatingEmailChallenge() throws Exception {
        fakeTelegram.operational = false;
        fakeEmail.operational = false;

        rawLogin().andExpect(status().isServiceUnavailable());

        assertThat(redisTemplate.keys("auth:login-challenge:*")).isEmpty();
    }

    @Test
    void telegramRejectionBlocksDeviceAndExpiredChallengeNeverGrantsAccess() throws Exception {
        MvcResult pending = rawLogin().andExpect(status().isAccepted()).andReturn();
        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        Cookie device = pending.getResponse().getCookie("workflow_device");
        telegramUpdate(Map.of("callback_query", Map.of("data", "auth:reject:" + id, "from", Map.of("id", 777))))
                .andExpect(status().isNoContent());
        assertThat(redisTemplate.hasKey(
                        "auth:blocked-device:" + USER_ID + ":" + deviceCookie.deviceId(device.getValue())))
                .isTrue();
        mockMvc.perform(get("/api/auth/challenges/{id}", id).cookie(device))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/auth/login")
                        .cookie(device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isForbidden());

        MvcResult other = rawLogin().andExpect(status().isAccepted()).andReturn();
        String otherId = objectMapper
                .readTree(other.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        redisTemplate.expire("auth:login-challenge:" + otherId, Duration.ofMillis(1));
        Thread.sleep(20);
        mockMvc.perform(get("/api/auth/challenges/{id}", otherId)
                        .cookie(other.getResponse().getCookie("workflow_device")))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void rejectionTerminatesExistingSessionsForTheDevice() throws Exception {
        MvcResult existing = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie access = existing.getResponse().getCookie(COOKIE_NAME);
        Cookie refresh = existing.getResponse().getCookie("workflow_refresh_token");
        redisTemplate.delete("auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(DEVICE.getValue()));
        MvcResult pending =
                login(EMAIL, PASSWORD).andExpect(status().isAccepted()).andReturn();
        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        telegramUpdate(Map.of("callback_query", Map.of("data", "auth:reject:" + id, "from", Map.of("id", 777))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/test").cookie(access)).andExpect(status().isUnauthorized());
        CsrfCredentials csrf = csrfCredentials();
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refresh, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isUnauthorized());
        assertThat(redisTemplate.keys("auth:refresh-session:*")).isEmpty();
    }

    @Test
    void superadminCannotForceEmailWhenTelegramWorks() throws Exception {
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);
        MvcResult pending = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", EMAIL, "password", PASSWORD, "confirmationMethod", "EMAIL"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_TELEGRAM"))
                .andReturn();
        assertThat(fakeTelegram.challenge).isNotNull();
        assertThat(fakeEmail.code).isNull();
        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        Cookie device = pending.getResponse().getCookie("workflow_device");
        CsrfCredentials csrf = csrfCredentials();
        telegramUpdate(Map.of("callback_query", Map.of("data", "auth:approve:" + id, "from", Map.of("id", 777))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPERADMIN"));
    }

    @Test
    void trustedSuperadminDoesNotCreateChallengeForRequestedEmail() throws Exception {
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", EMAIL, "password", PASSWORD, "confirmationMethod", "EMAIL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPERADMIN"));

        assertThat(fakeTelegram.challenge).isNull();
        assertThat(fakeEmail.code).isNull();
    }

    @Test
    void superadminFallsBackToEmailWhenTelegramIsUnavailable() throws Exception {
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);
        fakeTelegram.operational = false;

        rawLogin()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"));

        assertThat(fakeTelegram.challenge).isNull();
        assertThat(fakeEmail.code).matches("[0-9]{6}");
    }

    @Test
    void unboundSuperadminFallsBackToEmail() throws Exception {
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);
        jdbcTemplate.update("update users set telegram_chat_id = null where id = ?", USER_ID);

        MvcResult pending = rawLogin()
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL"))
                .andReturn();

        assertThat(fakeTelegram.challenge).isNull();
        assertThat(fakeEmail.code).matches("[0-9]{6}");
        String id = objectMapper
                .readTree(pending.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        assertThat(redisTemplate.keys("auth:login-challenge:*"))
                .containsExactlyInAnyOrder("auth:login-challenge:" + id, "auth:login-challenge:" + id + ":resend");
    }

    @Test
    void superadminUnbindingRevokesSessionsAndOrdinaryUserCannotUseAdminRoute() throws Exception {
        MvcResult manager = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        CsrfCredentials csrf = csrfCredentials();
        jdbcTemplate.update("update users set telegram_chat_id = null where id = ?", USER_ID);
        mockMvc.perform(post("/api/users/{id}/telegram/link", USER_ID)
                        .cookie(manager.getResponse().getCookie(COOKIE_NAME), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/users/{id}/telegram", USER_ID)
                        .cookie(manager.getResponse().getCookie(COOKIE_NAME), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);
        mockMvc.perform(post("/api/auth/telegram/link")
                        .cookie(manager.getResponse().getCookie(COOKIE_NAME), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link")
                        .value(org.hamcrest.Matchers.startsWith("https://t.me/workflow_test_bot?start=")));
        jdbcTemplate.update("update users set telegram_chat_id = 777 where id = ?", USER_ID);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/users/{id}/telegram", USER_ID)
                        .cookie(manager.getResponse().getCookie(COOKIE_NAME), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select telegram_chat_id from users where id = ?", Long.class, USER_ID))
                .isNull();
        mockMvc.perform(get("/api/test").cookie(manager.getResponse().getCookie(COOKIE_NAME)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTelegramRouteUsesCurrentDatabaseRole() throws Exception {
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'SUPERADMIN') where id = ?", USER_ID);
        MvcResult admin = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        jdbcTemplate.update(
                "update users set role_id = (select id from roles where code = 'MANAGER') where id = ?", USER_ID);
        jdbcTemplate.update("update users set telegram_chat_id = 777 where id = ?", USER_ID);
        CsrfCredentials csrf = csrfCredentials();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/users/{id}/telegram", USER_ID)
                        .cookie(admin.getResponse().getCookie(COOKIE_NAME), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void limitsChallengesToThreePerMinuteAndResendToTenSeconds() throws Exception {
        MvcResult first = rawLogin().andExpect(status().isAccepted()).andReturn();
        String id = objectMapper
                .readTree(first.getResponse().getContentAsByteArray())
                .get("challengeId")
                .asText();
        CsrfCredentials csrf = csrfCredentials();
        mockMvc.perform(post("/api/auth/challenges/{id}/resend", id)
                        .cookie(first.getResponse().getCookie("workflow_device"), csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isTooManyRequests());
        rawLogin().andExpect(status().isAccepted());
        rawLogin().andExpect(status().isAccepted());
        rawLogin().andExpect(status().isTooManyRequests());
    }

    @Test
    void returnsSameGenericResponseForUnknownEmailAndWrongPassword() throws Exception {
        String unknownEmailResponse = login("missing@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String wrongPasswordResponse = login(EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> unknownError = objectMapper.readValue(unknownEmailResponse, new TypeReference<>() {});
        Map<String, Object> wrongPasswordError =
                objectMapper.readValue(wrongPasswordResponse, new TypeReference<>() {});
        assertThat(unknownError.get("message")).isEqualTo("Invalid email or password");
        assertThat(wrongPasswordError.get("message")).isEqualTo("Invalid email or password");
    }

    @Test
    void returnsForbiddenForDisabledUser() throws Exception {
        jdbcTemplate.update("update users set status = 'DISABLED' where id = ?", USER_ID);

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void temporarilyBlocksLoginAfterFiveFailures() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            login(EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"));

        Set<String> keys = redisTemplate.keys("auth:login-attempts:*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, 900L);
        redisTemplate.expire(key, Duration.ofMillis(1));
        Thread.sleep(20);
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredTokenOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/test").cookie(expiredCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void permitsNewLoginWithExpiredCookie() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .cookie(expiredCookie(), DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test").cookie(new Cookie(COOKIE_NAME, "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsUsersWithoutRegionAndResetsFailedAttempts() throws Exception {
        UserRegionTestData.replace(jdbcTemplate, USER_ID);
        login(EMAIL, "wrong-password").andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").isEmpty());
        assertThat(redisTemplate.keys("auth:login-attempts:*")).isEmpty();
    }

    @Test
    void neverLogsPasswordIncludingMalformedRequests(CapturedOutput output) throws Exception {
        login(EMAIL, PASSWORD).andExpect(status().isOk());
        login(EMAIL, "sensitive-invalid-password").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manager@example.com\",\"password\":\"sensitive-malformed-password\""))
                .andExpect(status().isBadRequest());
        assertThat(output.getAll())
                .doesNotContain(PASSWORD, "sensitive-invalid-password", "sensitive-malformed-password");
    }

    @Test
    void logoutClearsCookieAndRejectsReuseOfRevokedToken(CapturedOutput output) throws Exception {
        Cookie cookie = loginCookie();
        var jwt = jwtDecoder.decode(cookie.getValue());
        CsrfCredentials csrf = csrfCredentials();

        MvcResult result =
                logout(cookie, csrf).andExpect(status().isNoContent()).andReturn();

        Cookie cleared = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                        .filter(value -> value.startsWith(COOKIE_NAME + "="))
                        .findFirst()
                        .orElseThrow())
                .contains("Path=/", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=0");
        assertThat(result.getResponse().getContentAsString()).isEmpty();

        String key = "auth:revoked-token:" + jwt.getId();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("1");
        long remainingMillis =
                Duration.between(Instant.now(), jwt.getExpiresAt()).toMillis();
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(remainingMillis - 1000, remainingMillis + 1000);
        assertThat(redisTemplate.keys("auth:revoked-token:*")).containsExactly(key);

        mockMvc.perform(get("/api/test").cookie(cookie)).andExpect(status().isUnauthorized());
        logout(cookie, csrf).andExpect(status().isUnauthorized());
        assertThat(output.getAll()).doesNotContain(cookie.getValue());
    }

    @Test
    void logoutDoesNotRevokeOtherLogins() throws Exception {
        Cookie first = loginCookie();
        Cookie second = loginCookie();

        logout(first, csrfCredentials()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/test").cookie(second)).andExpect(status().isOk());
        Cookie next = loginCookie();
        mockMvc.perform(get("/api/test").cookie(next)).andExpect(status().isOk());
    }

    @Test
    void logoutWithExpiredTokenReturnsUnauthorizedWithoutServerError() throws Exception {
        CsrfCredentials csrf = csrfCredentials();

        logout(expiredCookie(), csrf).andExpect(status().isUnauthorized());

        assertThat(redisTemplate.keys("auth:revoked-token:*")).isEmpty();
    }

    @Test
    void logoutRejectsInvalidOrMissingAuthentication() throws Exception {
        CsrfCredentials csrf = csrfCredentials();
        logout(new Cookie(COOKIE_NAME, "invalid-token"), csrf).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout").cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isUnauthorized());
        assertThat(redisTemplate.keys("auth:revoked-token:*")).isEmpty();
    }

    @Test
    void logoutRequiresValidCsrfToken() throws Exception {
        Cookie cookie = loginCookie();
        CsrfCredentials csrf = csrfCredentials();

        mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookie, csrf.cookie())
                        .header(csrf.headerName(), "incorrect-csrf-token"))
                .andExpect(status().isForbidden());

        assertThat(redisTemplate.keys("auth:revoked-token:*")).isEmpty();
        mockMvc.perform(get("/api/test").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void csrfTokenCanBeObtainedWithExpiredAuthenticationCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").cookie(expiredCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("XSRF-TOKEN=", "HttpOnly", "Secure", "Path=/");
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite"))
                .isEqualTo("Strict");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void csrfCookieKeepsSecurityAttributesOverHttp() {
        var response = RestClient.create("http://localhost:" + serverPort)
                .get()
                .uri("/api/auth/csrf")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("XSRF-TOKEN=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void redisRemovesRevocationAtExpirationAndSkipsAlreadyExpiredTokens() {
        String tokenId = UUID.randomUUID().toString();
        Instant expiration = Instant.now().plusSeconds(2);
        revocationStore.revoke(tokenId, expiration);

        assertThat(revocationStore.isRevoked(tokenId)).isTrue();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(redisTemplate.hasKey("auth:revoked-token:" + tokenId))
                        .isFalse());

        revocationStore.revoke(tokenId, expiration);
        assertThat(redisTemplate.keys("auth:revoked-token:*")).isEmpty();
    }

    private Cookie loginCookie() throws Exception {
        return login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(COOKIE_NAME);
    }

    private CsrfCredentials csrfCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new CsrfCredentials(
                result.getResponse().getCookie("XSRF-TOKEN"),
                body.get("headerName").asText(),
                body.get("token").asText());
    }

    private org.springframework.test.web.servlet.ResultActions logout(Cookie cookie, CsrfCredentials csrf)
            throws Exception {
        return mockMvc.perform(
                post("/api/auth/logout").cookie(cookie, csrf.cookie()).header(csrf.headerName(), csrf.token()));
    }

    private record CsrfCredentials(Cookie cookie, String headerName, String token) {}

    private Cookie expiredCookie() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("workflow-crm-test")
                .subject(USER_ID.toString())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.minusSeconds(1))
                .id(UUID.randomUUID().toString())
                .claim("role", "MANAGER")
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Cookie(COOKIE_NAME, token);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        String request = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(post("/api/auth/login")
                .cookie(DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private org.springframework.test.web.servlet.ResultActions rawLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))));
    }

    private org.springframework.test.web.servlet.ResultActions telegramUpdate(Map<String, Object> update)
            throws Exception {
        return mockMvc.perform(post("/api/auth/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)));
    }

    private void insertRegion() {
        jdbcTemplate.update("insert into regions (id, code, name) values (?, 'TEST_REGION', 'Test Region')", REGION_ID);
    }

    private void insertUser(String status) {
        UUID roleId = jdbcTemplate.queryForObject("select id from roles where code = 'MANAGER'", UUID.class);
        jdbcTemplate.update(
                "insert into users (id, email, password_hash, full_name, role_id, status) "
                        + "values (?, ?, ?, ?, ?, ?)",
                USER_ID,
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "Test Manager",
                roleId,
                status);
        UserRegionTestData.assign(jdbcTemplate, USER_ID, REGION_ID);
    }

    private void flushRedis() {
        try (RedisConnection connection = java.util.Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @TestConfiguration
    static class ProtectedEndpointConfiguration {

        @Bean
        @Primary
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }

        @Bean
        @Primary
        FakeTelegramSender fakeTelegramSender() {
            return new FakeTelegramSender();
        }

        @RestController
        static class ProtectedController {

            @GetMapping("/api/test")
            Map<String, String> test() {
                return Map.of("status", "ok");
            }
        }
    }

    static class FakeEmailSender implements EmailChallengeSender {
        volatile String code;
        volatile boolean operational = true;
        volatile boolean failDelivery;

        @Override
        public boolean available() {
            return operational;
        }

        @Override
        public void send(String email, String code) {
            if (failDelivery) {
                throw new IllegalStateException("Email delivery failed");
            }
            this.code = code;
        }

        void reset() {
            code = null;
            operational = true;
            failDelivery = false;
        }
    }

    static class FakeTelegramSender implements TelegramChallengeSender {
        volatile LoginChallenge challenge;
        volatile boolean operational = true;
        volatile boolean failDelivery;

        @Override
        public boolean available() {
            return operational;
        }

        @Override
        public void send(long chatId, LoginChallenge challenge) {
            this.challenge = challenge;
            if (failDelivery) {
                throw new IllegalStateException("Telegram delivery failed");
            }
        }

        void reset() {
            challenge = null;
            operational = true;
            failDelivery = false;
        }
    }
}
