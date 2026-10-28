package dev.workflowcrm.auth.domain;

public interface PasswordResetSender {
    boolean available();

    void send(String email, String fullName, String token);
}
