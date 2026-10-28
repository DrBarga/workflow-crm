package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.TelegramBotGateway;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class TelegramBotClient implements TelegramBotGateway {
    private final RestClient client;
    private final TelegramProperties properties;

    TelegramBotClient(RestClient.Builder builder, TelegramProperties properties) {
        this.client = builder.build();
        this.properties = properties;
    }

    @Override
    public void sendMessage(long chatId, String text, List<Button> buttons) {
        if (!properties.configured()) {
            throw new IllegalStateException("Telegram bot is not configured");
        }
        Map<String, Object> body = buttons.isEmpty()
                ? Map.of("chat_id", chatId, "text", text)
                : Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "reply_markup",
                                Map.of(
                                        "inline_keyboard",
                                        List.of(buttons.stream()
                                                .map(button ->
                                                        Map.of("text", button.text(), "callback_data", button.data()))
                                                .toList())));
        post("sendMessage", body);
    }

    @Override
    public void answerCallback(String callbackId, String text) {
        if (callbackId == null || callbackId.isBlank()) {
            return;
        }
        post("answerCallbackQuery", Map.of("callback_query_id", callbackId, "text", text));
    }

    private void post(String method, Map<String, Object> body) {
        try {
            client.post()
                    .uri("https://api.telegram.org/bot" + properties.botToken() + "/" + method)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to deliver Telegram response");
        }
    }
}
