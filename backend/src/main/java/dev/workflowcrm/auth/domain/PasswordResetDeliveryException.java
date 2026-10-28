package dev.workflowcrm.auth.domain;

public final class PasswordResetDeliveryException extends RuntimeException {
    public PasswordResetDeliveryException(Throwable cause) {
        super("Password reset email delivery failed", cause);
    }
}
