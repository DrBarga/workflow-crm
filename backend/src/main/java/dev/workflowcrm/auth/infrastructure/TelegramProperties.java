package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.TelegramSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.telegram")
public record TelegramProperties(String botUsername, String botToken, String webhookSecret)
        implements TelegramSettings {
    public boolean configured() {
        return botUsername != null
                && !botUsername.isBlank()
                && botToken != null
                && !botToken.isBlank()
                && webhookSecret != null
                && !webhookSecret.isBlank();
    }

    @Override
    public String toString() {
        return "TelegramProperties[botUsername=" + botUsername + ", secrets redacted]";
    }
}
