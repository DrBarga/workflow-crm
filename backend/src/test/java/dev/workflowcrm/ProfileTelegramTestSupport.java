package dev.workflowcrm;

import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import dev.workflowcrm.auth.domain.BindingTokens;
import dev.workflowcrm.auth.domain.LoginFlowService;
import dev.workflowcrm.auth.domain.TelegramBotGateway;
import dev.workflowcrm.users.api.TelegramBinding;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
        properties = {
            "app.security.telegram.bot-username=workflow_test_bot",
            "app.security.telegram.bot-token=test-bot-token",
            "app.security.telegram.webhook-secret=profile-test-secret"
        })
abstract class ProfileTelegramTestSupport extends ProfileTelegramHttpSupport {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BindingTokens tokens;

    @Autowired
    LoginFlowService flow;

    @Autowired
    TelegramBinding binding;

    @Autowired
    StringRedisTemplate redis;

    @MockitoBean
    TelegramBotGateway bot;

    final UUID user = UUID.randomUUID(), other = UUID.randomUUID();

    @BeforeEach
    void setupProfile() throws Exception {
        reset(bot);
        new ProfileTelegramFixtures(jdbc, tokens).create(user, other);
    }

    @AfterEach
    void cleanupProfile() {
        new ProfileTelegramFixtures(jdbc, tokens).remove(user, other);
    }

    void start(String command, long chat) throws Exception {
        new ProfileTelegramWebhook(mvc, json).start(command, chat);
    }

    void decide(String token, long chat, boolean approve) throws Exception {
        new ProfileTelegramWebhook(mvc, json).decide(token, chat, approve);
    }

    void webhook(Map<String, Object> update) throws Exception {
        new ProfileTelegramWebhook(mvc, json).webhook(update);
    }

    int events(UUID actor) {
        return jdbc.queryForObject(
                "select count(*) from telegram_binding_events where user_id=?", Integer.class, actor);
    }
}
