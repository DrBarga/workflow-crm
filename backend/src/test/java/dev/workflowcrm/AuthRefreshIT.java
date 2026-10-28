package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
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
import org.springframework.test.web.servlet.ResultActions;
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
@ExtendWith(OutputCaptureExtension.class)
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
            "app.security.jwt.issuer=workflow-refresh-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.refresh-cookie-name=workflow_refresh_token",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.jwt.refresh-token-ttl=7d",
            "app.security.jwt.secure=true"
        })
@Import(AuthRefreshIT.ProtectedEndpointConfiguration.class)
class AuthRefreshIT {

    private static final String ACCESS = "workflow_access_token";
    private static final String REFRESH = "workflow_refresh_token";
    private static final String SESSION_PREFIX = "auth:refresh-session:";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0003-000000000001");
    private static final Cookie DEVICE = new Cookie(
            "workflow_device", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));

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
    private JwtEncoder encoder;

    @Autowired
    private DeviceCookie deviceCookie;

    @BeforeEach
    void setUp() {
        database.update("delete from users");
        database.update(
                "insert into users (id, email, password_hash, full_name, role_id, status) "
                        + "values (?, 'refresh@example.com', ?, 'Refresh User', "
                        + "(select id from roles where code = 'MANAGER'), 'ACTIVE')",
                USER_ID,
                passwords.encode("test-password"));
        try (var connection =
                java.util.Objects.requireNonNull(redis.getConnectionFactory()).getConnection()) {
            connection.serverCommands().flushDb();
        }
        database.update("update users set telegram_chat_id = 778 where id = ?", USER_ID);
        trust(USER_ID);
    }

    @Test
    void loginCreatesSecureRefreshCookieAndStoresOnlyItsHash(CapturedOutput output) throws Exception {
        MvcResult result = login();
        Cookie refresh = result.getResponse().getCookie(REFRESH);
        assertThat(refresh).isNotNull();
        assertThat(refresh.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(refresh.getMaxAge()).isEqualTo(604800);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(header -> assertThat(header)
                .contains(REFRESH + "=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth"));
        String key = sessionKey(result);
        assertThat(redis.keys(SESSION_PREFIX + "*")).containsExactly(key);
        assertThat(key).doesNotContain(refresh.getValue());
        assertThat(redis.opsForHash().get(key, "userId")).isEqualTo(USER_ID.toString());
        assertThat(redis.opsForHash().entries(tokenKey(refresh)))
                .containsEntry("used", "false")
                .containsKeys("createdAt", "deviceId", "userAgent", "ip", "usedAt", "replacedByTokenId");
        assertThat(redis.getExpire(key)).isBetween(604790L, 604800L);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refresh.getValue());
        assertThat(output.getAll()).doesNotContain(refresh.getValue(), "test-password");
    }

    @Test
    void rotatesBothTokensAndExtendsTheSessionWithAnIdenticalGraceResponse() throws Exception {
        MvcResult login = login();
        Cookie refresh = login.getResponse().getCookie(REFRESH);
        String key = sessionKey(login);
        redis.expire(key, Duration.ofMinutes(30));
        Csrf csrf = csrf();

        MvcResult result =
                refresh(refresh, csrf).andExpect(status().isNoContent()).andReturn();
        Cookie access = result.getResponse().getCookie(ACCESS);
        assertThat(access.getValue())
                .isNotEqualTo(login.getResponse().getCookie(ACCESS).getValue());
        Cookie replacement = result.getResponse().getCookie(REFRESH);
        assertThat(replacement).isNotNull();
        assertThat(replacement.getValue()).isNotEqualTo(refresh.getValue());
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/", "Max-Age=900");
        assertThat(decoder.decode(access.getValue()).getSubject()).isEqualTo(USER_ID.toString());
        assertThat(redis.getExpire(key)).isBetween(604790L, 604800L);
        assertThat(redis.opsForHash().get(tokenKey(refresh), "used")).isEqualTo("true");
        assertThat(redis.opsForHash().get(tokenKey(refresh), "replacedByTokenId"))
                .isEqualTo(tokenKey(replacement).substring("auth:refresh-token:".length()));
        MvcResult repeated =
                refresh(refresh, csrf).andExpect(status().isNoContent()).andReturn();
        assertThat(repeated.getResponse().getCookie(ACCESS).getValue()).isEqualTo(access.getValue());
        assertThat(repeated.getResponse().getCookie(REFRESH).getValue()).isEqualTo(replacement.getValue());
        protectedRequest(access).andExpect(status().isOk());
    }

    @Test
    void refreshIgnoresExpiredAndMalformedAccessCookies() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        for (Cookie access : List.of(expiredAccess(), new Cookie(ACCESS, "invalid"))) {
            mvc.perform(post("/api/auth/refresh")
                            .cookie(refresh, access, csrf.cookie())
                            .header(csrf.header(), csrf.token()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void rejectsMissingMalformedUnknownAndAccessTokensAsRefresh() throws Exception {
        Csrf csrf = csrf();
        mvc.perform(post("/api/auth/refresh").cookie(csrf.cookie()).header(csrf.header(), csrf.token()))
                .andExpect(status().isUnauthorized());
        for (String value : List.of(
                "invalid",
                "a".repeat(43),
                login().getResponse().getCookie(ACCESS).getValue())) {
            refresh(new Cookie(REFRESH, value), csrf).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsExpiredRefreshAndItsAccessWithoutRecreatingTheSession() throws Exception {
        MvcResult login = login();
        String key = sessionKey(login);
        redis.expire(key, Duration.ofMillis(20));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redis.hasKey(key)));
        refresh(login.getResponse().getCookie(REFRESH), csrf()).andExpect(status().isUnauthorized());
        protectedRequest(login.getResponse().getCookie(ACCESS)).andExpect(status().isUnauthorized());
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void requiresCsrfAndDoesNotConsumeRefreshOnFailure() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(refresh, csrf.cookie()).header(csrf.header(), "invalid"))
                .andExpect(status().isForbidden());
        refresh(refresh, csrf).andExpect(status().isNoContent());
    }

    @Test
    void refreshUsesCurrentRoleAndRegion() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        database.update("update users set role_id = (select id from roles where code = 'CEO') where id = ?", USER_ID);
        UserRegionTestData.replace(database, USER_ID);
        Cookie access = refresh(refresh, csrf())
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getCookie(ACCESS);
        var token = decoder.decode(access.getValue());
        assertThat(token.getClaimAsString("role")).isEqualTo("CEO");
        assertThat(token.getClaims()).doesNotContainKey("region_id");
    }

    @Test
    void disabledAccountCannotRefreshEvenIfReenabledLater() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        database.update("update users set status = 'DISABLED' where id = ?", USER_ID);
        refresh(refresh, csrf).andExpect(status().isUnauthorized());
        database.update("update users set status = 'ACTIVE' where id = ?", USER_ID);
        refresh(refresh, csrf).andExpect(status().isUnauthorized());
        assertThat(redis.keys(SESSION_PREFIX + "*")).isEmpty();
    }

    @Test
    void deletedAccountCannotRefresh() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        database.update("delete from users where id = ?", USER_ID);
        refresh(refresh, csrf()).andExpect(status().isUnauthorized());
        assertThat(redis.keys(SESSION_PREFIX + "*")).isEmpty();
    }

    @Test
    void logoutRevokesAllAccessFromCurrentSessionButKeepsOtherLogins() throws Exception {
        MvcResult first = login();
        MvcResult second = login();
        Csrf csrf = csrf();
        Cookie renewed = refresh(first.getResponse().getCookie(REFRESH), csrf)
                .andReturn()
                .getResponse()
                .getCookie(ACCESS);
        MvcResult result = logout(first.getResponse().getCookie(ACCESS), csrf)
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cleared = result.getResponse().getCookie(REFRESH);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/api/auth");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(header -> assertThat(header)
                .contains(REFRESH + "=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict"));
        refresh(first.getResponse().getCookie(REFRESH), csrf).andExpect(status().isUnauthorized());
        protectedRequest(renewed).andExpect(status().isUnauthorized());
        refresh(second.getResponse().getCookie(REFRESH), csrf).andExpect(status().isNoContent());
        protectedRequest(second.getResponse().getCookie(ACCESS)).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshRequestsBothSucceedWithoutChangingTheSession() throws Exception {
        MvcResult login = login();
        Cookie refresh = login.getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return refresh(refresh, csrf).andReturn();
            });
            var second = executor.submit(() -> {
                start.await();
                return refresh(refresh, csrf).andReturn();
            });
            start.countDown();
            MvcResult one = first.get(10, TimeUnit.SECONDS);
            MvcResult two = second.get(10, TimeUnit.SECONDS);
            assertThat(one.getResponse().getStatus()).isEqualTo(204);
            assertThat(two.getResponse().getStatus()).isEqualTo(204);
            Cookie accessOne = one.getResponse().getCookie(ACCESS);
            Cookie accessTwo = two.getResponse().getCookie(ACCESS);
            assertThat(accessOne.getValue()).isEqualTo(accessTwo.getValue());
            assertThat(one.getResponse().getCookie(REFRESH).getValue())
                    .isEqualTo(two.getResponse().getCookie(REFRESH).getValue());
            protectedRequest(accessOne).andExpect(status().isOk());
            protectedRequest(accessTwo).andExpect(status().isOk());
            assertThat(redis.keys(SESSION_PREFIX + "*")).containsExactly(sessionKey(login));
            refresh(refresh, csrf).andExpect(status().isNoContent());
        }
    }

    @Test
    void concurrentLogoutCannotBeUndoneByRefresh() throws Exception {
        MvcResult login = login();
        Cookie access = login.getResponse().getCookie(ACCESS);
        Cookie refresh = login.getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var refreshing = executor.submit(() -> {
                start.await();
                return refresh(refresh, csrf).andReturn();
            });
            var loggingOut = executor.submit(() -> {
                start.await();
                return logout(access, csrf).andReturn();
            });
            start.countDown();
            MvcResult refreshed = refreshing.get(10, TimeUnit.SECONDS);
            assertThat(loggingOut.get(10, TimeUnit.SECONDS).getResponse().getStatus())
                    .isEqualTo(204);
            assertThat(refreshed.getResponse().getStatus()).isIn(204, 401);
            Cookie renewed = refreshed.getResponse().getCookie(ACCESS);
            if (renewed != null) {
                protectedRequest(renewed).andExpect(status().isUnauthorized());
            }
            refresh(refresh, csrf).andExpect(status().isUnauthorized());
            assertThat(redis.keys(SESSION_PREFIX + "*")).isEmpty();
        }
    }

    @Test
    void refreshTokenDoesNotAuthenticateProtectedEndpoints() throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        protectedRequest(new Cookie(ACCESS, refresh.getValue())).andExpect(status().isUnauthorized());
        protectedRequest(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshNeverLogsOrReturnsTokensInTheBody(CapturedOutput output) throws Exception {
        Cookie refresh = login().getResponse().getCookie(REFRESH);
        MvcResult result =
                refresh(refresh, csrf()).andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(output.getAll())
                .doesNotContain(
                        refresh.getValue(),
                        result.getResponse().getCookie(ACCESS).getValue());
    }

    @Test
    void reuseOutsideGraceRevokesEveryDeviceAndAllowsANewLogin(CapturedOutput output) throws Exception {
        MvcResult first = login();
        MvcResult otherDevice = login();
        UUID unrelatedUser = UUID.fromString("00000000-0000-0000-0003-000000000004");
        database.update(
                "insert into users (id, email, password_hash, full_name, role_id, status) "
                        + "select ?, 'unrelated@example.com', password_hash, 'Unrelated User', role_id, 'ACTIVE' from users where id = ?",
                unrelatedUser,
                USER_ID);
        database.update("update users set telegram_chat_id = 779 where id = ?", unrelatedUser);
        trust(unrelatedUser);
        MvcResult unrelated = mvc.perform(post("/api/auth/login")
                        .cookie(DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unrelated@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        var legacyClaims = new java.util.HashMap<String, Object>(
                decoder.decode(first.getResponse().getCookie(ACCESS).getValue()).getClaims());
        legacyClaims.remove("sid");
        Cookie legacyAccess = new Cookie(
                ACCESS,
                encoder.encode(JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(),
                                JwtClaimsSet.builder()
                                        .claims(claims -> claims.putAll(legacyClaims))
                                        .build()))
                        .getTokenValue());
        protectedRequest(legacyAccess).andExpect(status().isOk());
        Cookie original = first.getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        MvcResult rotated =
                refresh(original, csrf).andExpect(status().isNoContent()).andReturn();
        redis.opsForHash()
                .put(
                        tokenKey(original),
                        "usedAt",
                        Long.toString(Instant.now().minusSeconds(31).toEpochMilli()));

        refresh(original, csrf).andExpect(status().isUnauthorized());
        protectedRequest(legacyAccess).andExpect(status().isUnauthorized());
        protectedRequest(unrelated.getResponse().getCookie(ACCESS)).andExpect(status().isOk());
        refresh(unrelated.getResponse().getCookie(REFRESH), csrf).andExpect(status().isNoContent());
        for (MvcResult session : List.of(first, otherDevice, rotated)) {
            protectedRequest(session.getResponse().getCookie(ACCESS)).andExpect(status().isUnauthorized());
            refresh(session.getResponse().getCookie(REFRESH), csrf).andExpect(status().isUnauthorized());
        }
        assertThat(output.getAll())
                .contains("Refresh token reuse detected; all sessions revoked for user " + USER_ID)
                .doesNotContain(
                        original.getValue(),
                        rotated.getResponse().getCookie(REFRESH).getValue());
        MvcResult newLogin = login();
        refresh(original, csrf).andExpect(status().isUnauthorized());
        protectedRequest(newLogin.getResponse().getCookie(ACCESS)).andExpect(status().isOk());
        refresh(newLogin.getResponse().getCookie(REFRESH), csrf).andExpect(status().isNoContent());
    }

    @Test
    void capsSlidingExpiryAtThirtyDaysFromLogin() throws Exception {
        MvcResult login = login();
        Cookie refresh = login.getResponse().getCookie(REFRESH);
        Instant ceiling = Instant.now().plus(Duration.ofDays(1));
        String key = sessionKey(login);
        redis.opsForHash()
                .put(
                        key,
                        "createdAt",
                        Long.toString(ceiling.minus(Duration.ofDays(30)).toEpochMilli()));
        redis.opsForHash().put(key, "absoluteExpiresAt", Long.toString(ceiling.toEpochMilli()));
        redis.opsForHash().put(tokenKey(refresh), "absoluteExpiresAt", Long.toString(ceiling.toEpochMilli()));

        MvcResult rotated =
                refresh(refresh, csrf()).andExpect(status().isNoContent()).andReturn();
        Cookie replacement = rotated.getResponse().getCookie(REFRESH);
        assertThat(replacement.getMaxAge()).isBetween(86390, 86400);
        assertThat(redis.opsForHash().get(key, "expiresAt")).isEqualTo(Long.toString(ceiling.toEpochMilli()));
        assertThat(redis.opsForHash().get(tokenKey(replacement), "absoluteExpiresAt"))
                .isEqualTo(Long.toString(ceiling.toEpochMilli()));
        redis.opsForHash()
                .put(
                        tokenKey(replacement),
                        "absoluteExpiresAt",
                        Long.toString(Instant.now().minusSeconds(1).toEpochMilli()));
        refresh(replacement, csrf()).andExpect(status().isUnauthorized());
    }

    @Test
    void storesServerIssuedDeviceIdentityAndIgnoresSpoofedHeader() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(DEVICE)
                        .header("X-Device-Id", "browser-profile-1")
                        .header("User-Agent", "Test browser")
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refresh@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<Object, Object> metadata = redis.opsForHash().entries(sessionKey(login));
        assertThat(metadata)
                .containsEntry("deviceId", deviceCookie.deviceId(DEVICE.getValue()))
                .containsEntry("userAgent", "Test browser")
                .containsEntry("ip", "127.0.0.1")
                .containsKey("createdAt");
        Cookie original = login.getResponse().getCookie(REFRESH);
        MvcResult rotated =
                refresh(original, csrf()).andExpect(status().isNoContent()).andReturn();
        Map<Object, Object> token =
                redis.opsForHash().entries(tokenKey(rotated.getResponse().getCookie(REFRESH)));
        assertThat(token)
                .containsEntry("deviceId", deviceCookie.deviceId(DEVICE.getValue()))
                .containsEntry("userAgent", "Test browser");
        String replayKey = "auth:refresh-replay:" + tokenKey(original).substring("auth:refresh-token:".length());
        assertThat(redis.getExpire(replayKey)).isBetween(1L, 30L);
        assertThat(redis.opsForValue().get(replayKey))
                .doesNotContain(
                        original.getValue(),
                        rotated.getResponse().getCookie(REFRESH).getValue(),
                        rotated.getResponse().getCookie(ACCESS).getValue());
    }

    @Test
    void detectsReuseEvenAfterTheOriginalSessionWasLoggedOut() throws Exception {
        MvcResult first = login();
        MvcResult other = login();
        Cookie original = first.getResponse().getCookie(REFRESH);
        Csrf csrf = csrf();
        refresh(original, csrf).andExpect(status().isNoContent());
        logout(first.getResponse().getCookie(ACCESS), csrf).andExpect(status().isNoContent());
        redis.opsForHash()
                .put(
                        tokenKey(original),
                        "usedAt",
                        Long.toString(Instant.now().minusSeconds(31).toEpochMilli()));
        refresh(original, csrf).andExpect(status().isUnauthorized());
        protectedRequest(other.getResponse().getCookie(ACCESS)).andExpect(status().isUnauthorized());
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .cookie(DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refresh@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void trust(UUID userId) {
        redis.opsForValue()
                .set(
                        "auth:trusted-device:" + userId + ":" + deviceCookie.deviceId(DEVICE.getValue()),
                        "1",
                        Duration.ofDays(30));
    }

    private String tokenKey(Cookie token) throws Exception {
        return "auth:refresh-token:"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(token.getValue().getBytes(StandardCharsets.US_ASCII)));
    }

    private ResultActions refresh(Cookie refresh, Csrf csrf) throws Exception {
        return mvc.perform(
                post("/api/auth/refresh").cookie(refresh, csrf.cookie()).header(csrf.header(), csrf.token()));
    }

    private ResultActions logout(Cookie access, Csrf csrf) throws Exception {
        return mvc.perform(
                post("/api/auth/logout").cookie(access, csrf.cookie()).header(csrf.header(), csrf.token()));
    }

    private ResultActions protectedRequest(Cookie access) throws Exception {
        return mvc.perform(get("/api/refresh-test").cookie(access));
    }

    private String sessionKey(MvcResult login) {
        return SESSION_PREFIX
                + decoder.decode(login.getResponse().getCookie(ACCESS).getValue())
                        .getClaimAsString("sid");
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

    private Cookie expiredAccess() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("workflow-refresh-test")
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

    private record Csrf(Cookie cookie, String header, String token) {}

    @TestConfiguration
    static class ProtectedEndpointConfiguration {
        @RestController
        static class ProtectedController {
            @GetMapping("/api/refresh-test")
            Map<String, String> check() {
                return Map.of("status", "ok");
            }
        }
    }
}
