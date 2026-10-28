package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.auth.domain.TelegramBindingDecision;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProfileTelegramReplacementIT extends ProfileTelegramTestSupport {
    @Test
    void confirmedReplacementOverwritesChatAndRecordsBothEvents() throws Exception {
        binding.bind(user, 50201);
        var revision = binding.state(user).revision();
        String token = link(user);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50201);
        start("/start " + token, 50202);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50201);
        decide(token, 50202, true);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50202);
        assertThat(binding.state(user).revision()).isNotEqualTo(revision);
        assertThat(jdbc.queryForList(
                        "select action from telegram_binding_events where user_id=? order by created_at",
                        String.class,
                        user))
                .containsExactlyInAnyOrder("BOUND", "UNBOUND", "REBOUND");
        assertThat(jdbc.queryForObject(
                        "select count(*) from telegram_binding_events where user_id=? and chat_id=? and action='UNBOUND'",
                        Integer.class,
                        user,
                        50201))
                .isEqualTo(1);
    }

    @Test
    void sameChatConfirmationStillChangesRevisionOnce() throws Exception {
        binding.bind(user, 50203);
        var revision = binding.state(user).revision();
        String token = link(user);
        decide(token, 50203, true);
        decide(token, 50203, true);
        assertThat(binding.state(user).revision()).isNotEqualTo(revision);
        assertThat(events(user)).isEqualTo(2);
    }

    @Test
    void occupiedTelegramDoesNotLoseExistingBinding() throws Exception {
        binding.bind(user, 50204);
        binding.bind(other, 50205);
        String token = link(user);
        start("/start " + token, 50205);
        verify(bot).sendMessage(eq(50205L), contains("уже привязан к другой"), eq(List.of()));
        decide(token, 50205, true);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50204);
        assertThat(binding.chatId(other).orElseThrow()).isEqualTo(50205);
        assertThat(events(user)).isEqualTo(1);
        assertThat(tokens.find(token)).isPresent();
        decide(token, 50206, true);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50206);
    }

    @Test
    void rejectionKeepsExistingBinding() throws Exception {
        binding.bind(user, 50207);
        String token = link(user);
        decide(token, 50208, false);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50207);
        assertThat(events(user)).isEqualTo(1);
    }

    @Test
    void legacyLinkCannotReplaceExistingChat() throws Exception {
        binding.bind(user, 50209);
        write(post("/api/auth/telegram/link"), user).andExpect(status().isConflict());
        String token = tokens.issue(user, "", Duration.ofMinutes(15));
        assertThat(flow.confirmTelegramBinding(token, 50210)).isEqualTo(TelegramBindingDecision.ACCOUNT_ALREADY_BOUND);
        assertThat(binding.chatId(user).orElseThrow()).isEqualTo(50209);
    }
}
