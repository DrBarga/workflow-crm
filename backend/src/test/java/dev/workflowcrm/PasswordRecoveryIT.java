package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.PasswordResetDeliveryException;
import dev.workflowcrm.auth.domain.PasswordResetSender;
import dev.workflowcrm.auth.infrastructure.DeviceCookie;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import org.springframework.test.web.servlet.MvcResult;
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
            "app.security.jwt.issuer=workflow-password-recovery-test",
            "app.security.jwt.cookie-name=workflow_access_token",
            "app.security.jwt.secure=true",
            "app.security.password-recovery.token-ttl=30m",
            "app.security.password-recovery.reset-url=https://crm.example.test/reset-password",
            "app.security.password-recovery.mail-from=no-reply@example.test"
        })
@Import(PasswordRecoveryIT.MailTestConfiguration.class)
class PasswordRecoveryIT {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0004-000000000001");
    private static final String EMAIL = "user@example.com";
    private static final String OLD_PASSWORD = "old-password-123";
    private static final String NEW_PASSWORD = "N7!mK4#qR9@x";
    private static final Cookie DEVICE = new Cookie(
            "workflow_device", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));

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
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private DeviceCookie deviceCookie;

    @BeforeEach
    void setUp() {
        database.update("delete from password_reset_tokens");
        database.update("delete from user_invitations");
        database.update("delete from user_access_history");
        database.update("delete from users");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        MailTestConfiguration.tokens.clear();
        MailTestConfiguration.available.set(true);
        MailTestConfiguration.failDelivery.set(false);
        insertUser(USER_ID, EMAIL, "ACTIVE", false, OLD_PASSWORD);
    }

    @Test
    void requestUsesThirtyMinuteHashedTokenAndDoesNotRevealAccounts() throws Exception {
        MvcResult existing = forgot(EMAIL).andExpect(status().isAccepted()).andReturn();
        String first = MailTestConfiguration.tokens.get(EMAIL);
        assertThat(first).isNotBlank();
        assertThat(existing.getResponse().getContentAsString()).isEmpty();
        assertThat(existing.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(database.queryForObject("select token_hash from password_reset_tokens", String.class))
                .isEqualTo(hash(first))
                .isNotEqualTo(first);
        OffsetDateTime expiry =
                database.queryForObject("select expires_at from password_reset_tokens", OffsetDateTime.class);
        assertThat(expiry.toInstant())
                .isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));

        MvcResult missing =
                forgot("missing@example.com").andExpect(status().isAccepted()).andReturn();
        assertThat(missing.getResponse().getContentAsString())
                .isEqualTo(existing.getResponse().getContentAsString());
        assertThat(missing.getResponse().getHeader("Cache-Control"))
                .isEqualTo(existing.getResponse().getHeader("Cache-Control"));

        forgot(EMAIL).andExpect(status().isAccepted());
        String replacement = MailTestConfiguration.tokens.get(EMAIL);
        assertThat(replacement).isNotEqualTo(first);
        assertThat(database.queryForObject(
                        "select count(*) from password_reset_tokens where invalidated_at is null", Integer.class))
                .isEqualTo(1);
        reset(first, NEW_PASSWORD).andExpect(status().isGone());
    }

    @Test
    void resetChangesPasswordOnceAndRevokesEveryExistingSession() throws Exception {
        MvcResult login = trustedLogin(OLD_PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie access = login.getResponse().getCookie("workflow_access_token");
        Cookie refresh = login.getResponse().getCookie("workflow_refresh_token");
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk());

        forgot(EMAIL).andExpect(status().isAccepted());
        String token = MailTestConfiguration.tokens.get(EMAIL);
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(passwords.matches(
                        NEW_PASSWORD,
                        database.queryForObject("select password_hash from users where id=?", String.class, USER_ID)))
                .isTrue();
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
        MvcResult csrf =
                mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/auth/refresh")
                        .cookie(refresh, csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header(
                                "X-XSRF-TOKEN",
                                json.readTree(csrf.getResponse().getContentAsByteArray())
                                        .get("token")
                                        .asText()))
                .andExpect(status().isUnauthorized());
        reset(token, "another-password-789").andExpect(status().isGone());
        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        trustedLogin(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void expiredTokenCannotChangePassword() throws Exception {
        forgot(EMAIL).andExpect(status().isAccepted());
        String token = MailTestConfiguration.tokens.get(EMAIL);
        database.update("update password_reset_tokens set expires_at=now()-interval '1 second'");

        reset(token, NEW_PASSWORD).andExpect(status().isGone());
        assertThat(passwords.matches(
                        OLD_PASSWORD,
                        database.queryForObject("select password_hash from users where id=?", String.class, USER_ID)))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "lowercase123!",
                "UPPERCASE123!",
                "WithoutDigits!",
                "WithoutSymbols12",
                "Password123!",
                "User!9283746A",
                "Bohdan!928374",
                "workflow!9283746"
            })
    void weakPasswordDoesNotConsumeLinkOrChangeAccount(String password) throws Exception {
        database.update("update users set full_name='Bohdan Zelenskyi' where id=?", USER_ID);
        MvcResult login = trustedLogin(OLD_PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie access = login.getResponse().getCookie("workflow_access_token");
        forgot(EMAIL).andExpect(status().isAccepted());
        String token = MailTestConfiguration.tokens.get(EMAIL);

        reset(token, password).andExpect(status().isBadRequest());
        assertThat(passwords.matches(
                        OLD_PASSWORD,
                        database.queryForObject("select password_hash from users where id=?", String.class, USER_ID)))
                .isTrue();
        assertThat(database.queryForObject("select consumed_at from password_reset_tokens", OffsetDateTime.class))
                .isNull();
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk());
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAndInvitationPendingAccountsLookLikeUnknownAccounts() throws Exception {
        database.update("update users set status='DISABLED' where id=?", USER_ID);
        MvcResult disabled = forgot(EMAIL).andExpect(status().isAccepted()).andReturn();
        MvcResult unknown =
                forgot("unknown@example.com").andExpect(status().isAccepted()).andReturn();
        assertThat(disabled.getResponse().getContentAsString())
                .isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(MailTestConfiguration.tokens).isEmpty();

        database.update("update users set status='ACTIVE', password_setup_required=true where id=?", USER_ID);
        forgot(EMAIL).andExpect(status().isAccepted());
        assertThat(MailTestConfiguration.tokens).isEmpty();
    }

    @Test
    void unavailableMailReturnsNeutralErrorAndLeavesNoUsableToken() throws Exception {
        MailTestConfiguration.available.set(false);
        MvcResult existing =
                forgot(EMAIL).andExpect(status().isServiceUnavailable()).andReturn();
        MvcResult unknown = forgot("unknown@example.com")
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        assertThat(json.readTree(existing.getResponse().getContentAsByteArray())
                        .get("message")
                        .asText())
                .isEqualTo("Password recovery is temporarily unavailable")
                .isEqualTo(json.readTree(unknown.getResponse().getContentAsByteArray())
                        .get("message")
                        .asText());
        assertThat(database.queryForObject("select count(*) from password_reset_tokens", Integer.class))
                .isZero();

        MailTestConfiguration.available.set(true);
        MailTestConfiguration.failDelivery.set(true);
        forgot(EMAIL).andExpect(status().isServiceUnavailable());
        assertThat(database.queryForObject(
                        "select count(*) from password_reset_tokens where invalidated_at is null", Integer.class))
                .isZero();
    }

    @Test
    void validatesEmailTokenAndPasswordWithoutAuthenticationOrCsrf() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        reset("unknown-token", "short").andExpect(status().isBadRequest());
        reset("unknown-token", NEW_PASSWORD).andExpect(status().isGone());
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .cookie(DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions trustedLogin(String password) throws Exception {
        redis.opsForValue()
                .set(
                        "auth:trusted-device:" + USER_ID + ":" + deviceCookie.deviceId(DEVICE.getValue()),
                        "1",
                        Duration.ofDays(30));
        return login(password);
    }

    private void insertUser(UUID id, String email, String status, boolean passwordSetupRequired, String password) {
        database.update(
                "insert into users(id,email,password_hash,password_setup_required,full_name,telegram_chat_id,role_id,status) "
                        + "values(?,?,?,?, 'Password User',4001,(select id from roles where code='SUPERADMIN'),?)",
                id,
                email,
                passwords.encode(password),
                passwordSetupRequired,
                status);
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration
    static class MailTestConfiguration {
        static final ConcurrentMap<String, String> tokens = new ConcurrentHashMap<>();
        static final AtomicBoolean available = new AtomicBoolean(true);
        static final AtomicBoolean failDelivery = new AtomicBoolean(false);

        @Bean
        @Primary
        PasswordResetSender passwordResetSender() {
            return new PasswordResetSender() {
                @Override
                public boolean available() {
                    return available.get();
                }

                @Override
                public void send(String email, String fullName, String token) {
                    if (failDelivery.get()) {
                        throw new PasswordResetDeliveryException(new IllegalStateException("SMTP unavailable"));
                    }
                    tokens.put(email, token);
                }
            };
        }
    }
}
