package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

final class ProfileTelegramWebhook {
    private final MockMvc mvc;
    private final ObjectMapper json;

    ProfileTelegramWebhook(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    void start(String command, long chat) throws Exception {
        webhook(Map.of(
                "message",
                Map.of("text", command, "chat", Map.of("id", chat, "type", "private"), "from", Map.of("id", chat))));
    }

    void decide(String token, long chat, boolean approve) throws Exception {
        webhook(Map.of(
                "callback_query",
                Map.of(
                        "id",
                        UUID.randomUUID().toString(),
                        "data",
                        "bind:" + (approve ? "approve:" : "reject:") + token,
                        "from",
                        Map.of("id", chat),
                        "message",
                        Map.of("chat", Map.of("id", chat, "type", "private")))));
    }

    void webhook(Map<String, Object> update) throws Exception {
        mvc.perform(post("/api/auth/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "profile-test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(update)))
                .andExpect(status().isNoContent());
    }
}
