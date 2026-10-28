package dev.workflowcrm.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.auth.infrastructure.TelegramProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/telegram")
class TelegramWebhookController {
    private final TelegramProperties properties;
    private final TelegramWebhookHandler handler;

    TelegramWebhookController(TelegramProperties properties, TelegramWebhookHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @PostMapping("/webhook")
    ResponseEntity<Void> update(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {
        if (!properties.configured()
                || secret == null
                || !MessageDigest.isEqual(
                        secret.getBytes(StandardCharsets.UTF_8),
                        properties.webhookSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        handler.handle(update);
        return ResponseEntity.noContent().build();
    }
}
