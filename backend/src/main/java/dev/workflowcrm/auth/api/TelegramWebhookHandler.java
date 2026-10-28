package dev.workflowcrm.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.auth.domain.LoginFlowService;
import dev.workflowcrm.auth.domain.TelegramBindingDecision;
import dev.workflowcrm.auth.domain.TelegramBindingPreview;
import dev.workflowcrm.auth.domain.TelegramBotGateway;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class TelegramWebhookHandler {
    private static final String EXPIRED =
            "Ссылка недействительна или срок её действия истёк. Запросите новую после входа в систему.";
    private static final String NO_TOKEN = "Для привязки Telegram войдите в систему и запросите ссылку.";

    private final LoginFlowService loginFlow;
    private final TelegramBotGateway bot;

    TelegramWebhookHandler(LoginFlowService loginFlow, TelegramBotGateway bot) {
        this.loginFlow = loginFlow;
        this.bot = bot;
    }

    void handle(JsonNode update) {
        JsonNode message = update.path("message");
        long chatId = privateChat(message.path("chat"), message.path("from"));
        String text = message.path("text").asText("").strip();
        if (chatId > 0 && text.matches("/start(?:@[A-Za-z0-9_]{5,32})?(?:\\s+.*)?")) {
            start(chatId, text);
            return;
        }

        JsonNode callback = update.path("callback_query");
        String data = callback.path("data").asText("");
        long fromId = callback.path("from").path("id").asLong(-1);
        String callbackId = callback.path("id").asText("");
        if (fromId <= 0) {
            return;
        }
        if (data.startsWith("auth:approve:")) {
            loginFlow.approveTelegram(data.substring("auth:approve:".length()), fromId);
            bot.answerCallback(callbackId, "Подтверждение принято");
        } else if (data.startsWith("auth:reject:")) {
            loginFlow.rejectTelegram(data.substring("auth:reject:".length()), fromId);
            bot.answerCallback(callbackId, "Вход отклонён");
        } else if (data.startsWith("bind:approve:") || data.startsWith("bind:reject:")) {
            long callbackChat = privateChat(callback.path("message").path("chat"), callback.path("from"));
            if (callbackChat != fromId) {
                return;
            }
            boolean approve = data.startsWith("bind:approve:");
            String token = data.substring(approve ? "bind:approve:".length() : "bind:reject:".length());
            var preview = loginFlow.previewTelegramBinding(token);
            TelegramBindingDecision decision =
                    approve ? loginFlow.confirmTelegramBinding(token, fromId) : loginFlow.rejectTelegramBinding(token);
            bot.answerCallback(callbackId, decision == TelegramBindingDecision.EXPIRED ? "Ссылка истекла" : "Готово");
            String response = outcome(decision);
            if (decision == TelegramBindingDecision.BOUND && preview.isPresent())
                response += "\nEmail: " + preview.get().email();
            bot.sendMessage(fromId, response, List.of());
        }
    }

    private void start(long chatId, String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            bot.sendMessage(chatId, NO_TOKEN, List.of());
            return;
        }
        String token = parts[1].strip();
        TelegramBindingPreview preview = loginFlow.previewTelegramBinding(token).orElse(null);
        if (preview == null) {
            bot.sendMessage(chatId, EXPIRED, List.of());
            return;
        }
        if (loginFlow.telegramChatInUse(token, chatId)) {
            bot.sendMessage(chatId, "Этот Telegram уже привязан к другой учётной записи.", List.of());
            return;
        }
        bot.sendMessage(
                chatId,
                "Подтвердите привязку Telegram к Workflow CRM.\nEmail: " + preview.email(),
                List.of(
                        new TelegramBotGateway.Button("Підтвердити", "bind:approve:" + token),
                        new TelegramBotGateway.Button("Це не я", "bind:reject:" + token)));
    }

    private String outcome(TelegramBindingDecision decision) {
        return switch (decision) {
            case BOUND -> "Telegram привязан.";
            case REJECTED -> "Привязка отклонена.";
            case EXPIRED -> EXPIRED;
            case CHAT_IN_USE -> "Этот Telegram уже привязан к другой учётной записи.";
            case ACCOUNT_ALREADY_BOUND -> "У этой учётной записи уже есть привязка Telegram.";
        };
    }

    private long privateChat(JsonNode chat, JsonNode from) {
        long chatId = chat.path("id").asLong(-1);
        return "private".equals(chat.path("type").asText(""))
                        && chatId > 0
                        && chatId == from.path("id").asLong(-1)
                ? chatId
                : -1;
    }
}
