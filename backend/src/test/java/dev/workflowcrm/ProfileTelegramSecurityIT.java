package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProfileTelegramSecurityIT extends ProfileTelegramTestSupport {
    @Test
    void profileAndWritesRequireAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/profile/telegram/link").cookie(csrf).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/profile/telegram/link").cookie(cookie(user))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/profile/telegram").cookie(cookie(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/profile").cookie(cookie(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.telegram.chatId").doesNotExist());
        write(post("/api/profile/telegram/link"), user)
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void disableAfterLinkInvalidatesBotConfirmation() throws Exception {
        String token = link(user);
        jdbc.update("update users set status='DISABLED' where id=?", user);
        decide(token, 50301, true);
        assertThat(binding.chatId(user)).isEmpty();
        assertThat(events(user)).isZero();
        mvc.perform(get("/api/profile").cookie(cookie(user))).andExpect(status().isForbidden());
    }

    @Test
    void unlinkInvalidatesPendingLinkAndRevokesSession() throws Exception {
        binding.bind(user, 50302);
        var access = cookie(user);
        String token = link(user);
        write(delete("/api/profile/telegram"), user).andExpect(status().isNoContent());
        assertThat(binding.chatId(user)).isEmpty();
        assertThat(tokens.find(token)).isEmpty();
        assertThat(events(user)).isEqualTo(2);
        mvc.perform(get("/api/profile").cookie(access)).andExpect(status().isUnauthorized());
        decide(token, 50303, true);
        assertThat(binding.chatId(user)).isEmpty();
    }

    @Test
    void forgedWebhookAndGroupCannotBind() throws Exception {
        String token = link(user);
        mvc.perform(post("/api/auth/telegram/webhook")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
        webhook(Map.of(
                "callback_query",
                Map.of(
                        "id",
                        "group",
                        "data",
                        "bind:approve:" + token,
                        "from",
                        Map.of("id", 50304),
                        "message",
                        Map.of("chat", Map.of("id", 50304, "type", "group")))));
        webhook(Map.of(
                "callback_query",
                Map.of(
                        "id",
                        "forged",
                        "data",
                        "bind:approve:" + token,
                        "from",
                        Map.of("id", 50304),
                        "message",
                        Map.of("chat", Map.of("id", 50305, "type", "private")))));
        assertThat(binding.chatId(user)).isEmpty();
        assertThat(events(user)).isZero();
        verifyNoInteractions(bot);
    }
}
