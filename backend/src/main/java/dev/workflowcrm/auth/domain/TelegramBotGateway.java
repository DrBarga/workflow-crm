package dev.workflowcrm.auth.domain;

import java.util.List;

public interface TelegramBotGateway {
    void sendMessage(long chatId, String text, List<Button> buttons);

    void answerCallback(String callbackId, String text);

    record Button(String text, String data) {}
}
