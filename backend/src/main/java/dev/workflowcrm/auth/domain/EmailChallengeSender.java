package dev.workflowcrm.auth.domain;

public interface EmailChallengeSender {
    boolean available();

    void send(String email, String code);
}
