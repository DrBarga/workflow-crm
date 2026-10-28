package dev.workflowcrm.auth.domain;

public interface TelegramChallengeSender {
    boolean available();

    void send(long chatId, LoginChallenge challenge);
}
