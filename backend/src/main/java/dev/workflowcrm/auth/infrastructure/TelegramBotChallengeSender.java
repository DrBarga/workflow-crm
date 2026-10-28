package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.LoginChallenge;
import dev.workflowcrm.auth.domain.TelegramChallengeSender;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class TelegramBotChallengeSender implements TelegramChallengeSender {
    private final RestClient client;
    private final TelegramProperties properties;
    private final Clock clock;

    TelegramBotChallengeSender(RestClient.Builder builder, TelegramProperties properties, Clock clock) {
        this.client = builder.build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean available() {
        return properties.configured();
    }

    @Override
    public void send(long chatId, LoginChallenge challenge) {
        if (!available()) {
            throw new IllegalStateException("Telegram login is not configured");
        }
        String text = "Workflow CRM login request\nIP: " + challenge.ip() + "\nBrowser: "
                + challenge.userAgent().replaceAll("[\\r\\n]+", " ")
                + "\nTime: " + DateTimeFormatter.ISO_INSTANT.format(clock.instant())
                + "\nApprove only if this was you.";
        Map<String, Object> buttons = Map.of(
                "inline_keyboard",
                List.of(List.of(
                        Map.of("text", "Approve", "callback_data", "auth:approve:" + challenge.id()),
                        Map.of("text", "Reject", "callback_data", "auth:reject:" + challenge.id()))));
        try {
            client.post()
                    .uri("https://api.telegram.org/bot" + properties.botToken() + "/sendMessage")
                    .body(Map.of("chat_id", chatId, "text", text, "reply_markup", buttons))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to deliver Telegram login request");
        }
    }
}
