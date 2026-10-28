package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.PasswordResetSender;
import dev.workflowcrm.users.domain.UserInvitationSender;
import dev.workflowcrm.users.infrastructure.LocalTestUserSeed;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        properties = {
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test",
            "app.minio.secret-key=test",
            "app.security.jwt.secret=test-jwt-signing-secret-with-32-bytes",
            "app.security.telegram.bot-token=",
            "app.security.telegram.bot-username=",
            "spring.mail.properties.mail.smtp.auth=true",
            "spring.mail.properties.mail.smtp.starttls.enable=false",
            "spring.mail.properties.mail.smtp.starttls.required=false",
            "spring.mail.properties.mail.smtp.ssl.enable=false",
            "app.security.email.from=sender@example.com",
            "app.users.mail-from=sender@example.com",
            "app.users.invitation-url=http://localhost/set-password",
            "app.security.password-recovery.mail-from=sender@example.com",
            "app.security.password-recovery.reset-url=http://localhost/reset-password",
            "app.dev.test-user.email=mail-tester@example.com",
            "app.dev.test-user.password=Local-mail-test-42!"
        })
class SmtpLoginIT {
    private static final String EMAIL = "mail-tester@example.com";
    private static final String PASSWORD = "Local-mail-test-42!";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> MAIL = new GenericContainer<>("axllent/mailpit:v1.27.4")
            .withExposedPorts(1025, 8025)
            .withEnv("MP_SMTP_AUTH", "smtp-test:synthetic-smtp-password")
            .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "true");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.mail.host", MAIL::getHost);
        registry.add("spring.mail.port", () -> MAIL.getMappedPort(1025));
        registry.add("spring.mail.username", () -> "smtp-test");
        registry.add("spring.mail.password", () -> "synthetic-smtp-password");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate database;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    JavaMailSenderImpl sender;

    @Autowired
    LocalTestUserSeed seed;

    @Autowired
    PasswordResetSender recovery;

    @Autowired
    UserInvitationSender invitations;

    private RestClient mailbox;

    @BeforeEach
    void reset() {
        mailbox = RestClient.create("http://" + MAIL.getHost() + ":" + MAIL.getMappedPort(8025));
        mailbox.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
        try (var connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        sender.setPassword("synthetic-smtp-password");
        sender.setPort(MAIL.getMappedPort(1025));
        database.update("update users set status='ACTIVE' where email=?", EMAIL);
    }

    @Test
    void startupCreatesUserAndReceivedSmtpCodeCompletesLogin(CapturedOutput output) throws Exception {
        assertThat(database.queryForObject("select count(*) from users where email=?", Integer.class, EMAIL))
                .isOne();
        assertThat(passwords.matches(
                        PASSWORD,
                        database.queryForObject("select password_hash from users where email=?", String.class, EMAIL)))
                .isTrue();
        assertThat(database.queryForObject(
                        "select password_setup_required from users where email=?", Boolean.class, EMAIL))
                .isFalse();
        MvcResult pending = login().andExpect(status().isAccepted())
                .andExpect(jsonPath("status").value("PENDING_EMAIL"))
                .andReturn();
        assertThat(pending.getResponse().getCookie("workflow_access_token")).isNull();
        Cookie device = pending.getResponse().getCookie("workflow_device");
        String id = json.readTree(pending.getResponse().getContentAsByteArray())
                .path("challengeId")
                .asText();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(messageCount()).isOne());
        JsonNode message =
                mailbox.get().uri("/api/v1/message/latest").retrieve().body(JsonNode.class);
        assertThat(message.path("To").get(0).path("Address").asText()).isEqualTo(EMAIL);
        var matcher =
                Pattern.compile("\\b[0-9]{6}\\b").matcher(message.path("Text").asText());
        assertThat(matcher.find()).isTrue();
        String code = matcher.group();
        MvcResult csrfResponse =
                mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode csrf = json.readTree(csrfResponse.getResponse().getContentAsByteArray());
        Cookie csrfCookie = csrfResponse.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrfCookie)
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("APPROVED"));
        MvcResult complete = mvc.perform(post("/api/auth/challenges/{id}/complete", id)
                        .cookie(device, csrfCookie)
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(complete.getResponse().getCookie("workflow_refresh_token")).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(complete.getResponse().getCookie("workflow_access_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("role").value("MANAGER"));
        mvc.perform(post("/api/auth/challenges/{id}/email", id)
                        .cookie(device, csrfCookie)
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isConflict());
        assertThat(output.getAll()).doesNotContain(code, PASSWORD, "synthetic-smtp-password");
    }

    @Test
    void wrongSmtpPasswordRejectsChallengeAndReturns503(CapturedOutput output) throws Exception {
        sender.setPassword("invalid-synthetic-password");
        login().andExpect(status().isServiceUnavailable());
        assertFailedDelivery();
        assertThat(output.getAll()).contains("SMTP authentication failed").doesNotContain("invalid-synthetic-password");
    }

    @Test
    void smtpConnectionFailureRejectsChallengeAndReturns503(CapturedOutput output) throws Exception {
        sender.setPort(1);
        login().andExpect(status().isServiceUnavailable());
        assertFailedDelivery();
        assertThat(output.getAll()).contains("SMTP connection or delivery failed");
    }

    @Test
    void repeatedSeedPreservesExistingAccountEvenWithChangedConfiguration() {
        String hash = database.queryForObject("select password_hash from users where email=?", String.class, EMAIL);
        database.update("update users set status='DISABLED' where email=?", EMAIL);
        new LocalTestUserSeed(database, passwords, " MAIL-TESTER@EXAMPLE.COM ", "changed-password-42!")
                .run(new DefaultApplicationArguments());
        assertThat(database.queryForObject("select count(*) from users where lower(email)=?", Integer.class, EMAIL))
                .isOne();
        assertThat(database.queryForObject("select password_hash from users where email=?", String.class, EMAIL))
                .isEqualTo(hash);
        assertThat(database.queryForObject("select status from users where email=?", String.class, EMAIL))
                .isEqualTo("DISABLED");
    }

    @Test
    void missingOrInvalidSeedConfigurationDoesNotCreateAccounts(CapturedOutput output) {
        Integer before = database.queryForObject("select count(*) from users", Integer.class);
        new LocalTestUserSeed(database, passwords, "", "").run(new DefaultApplicationArguments());
        new LocalTestUserSeed(database, passwords, "invalid-email", PASSWORD).run(new DefaultApplicationArguments());
        new LocalTestUserSeed(database, passwords, "other@example.com", "").run(new DefaultApplicationArguments());
        assertThat(database.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(before);
        assertThat(output.getAll()).contains("SEED_TEST_USER_EMAIL", "SEED_TEST_USER_PASSWORD");
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))));
    }

    private int messageCount() {
        return mailbox.get()
                .uri("/api/v1/messages")
                .retrieve()
                .body(JsonNode.class)
                .path("total")
                .asInt();
    }

    @Test
    void invitationAndRecoveryUseTheConfiguredSmtpTransport() {
        invitations.sendPasswordSetup(EMAIL, "Local tester", "synthetic-invitation-token");
        JsonNode invitation =
                mailbox.get().uri("/api/v1/message/latest").retrieve().body(JsonNode.class);
        assertThat(invitation.path("Text").asText())
                .contains("http://localhost/set-password?token=synthetic-invitation-token");
        recovery.send(EMAIL, "Local tester", "synthetic-reset-token");
        JsonNode reset = mailbox.get().uri("/api/v1/message/latest").retrieve().body(JsonNode.class);
        assertThat(reset.path("Text").asText()).contains("http://localhost/reset-password?token=synthetic-reset-token");
        assertThat(messageCount()).isEqualTo(2);
    }

    private void assertFailedDelivery() {
        assertThat(messageCount()).isZero();
        var keys = redis.keys("auth:login-challenge:*").stream()
                .filter(key -> !key.endsWith(":resend"))
                .toList();
        assertThat(keys).hasSize(1);
        assertThat(redis.opsForHash().get(keys.getFirst(), "status")).isEqualTo("REJECTED");
    }
}
