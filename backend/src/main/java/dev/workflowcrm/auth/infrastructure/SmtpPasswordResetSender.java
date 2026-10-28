package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.PasswordRecoveryProperties;
import dev.workflowcrm.auth.domain.PasswordResetDeliveryException;
import dev.workflowcrm.auth.domain.PasswordResetSender;
import dev.workflowcrm.shared.mail.SmtpMailDelivery;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
class SmtpPasswordResetSender implements PasswordResetSender {
    private final SmtpMailDelivery mail;
    private final PasswordRecoveryProperties properties;

    SmtpPasswordResetSender(SmtpMailDelivery mail, PasswordRecoveryProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return mail.available(properties.mailFrom(), "PASSWORD_RESET_MAIL_FROM")
                && !properties.resetUrl().isBlank();
    }

    @Override
    public void send(String email, String fullName, String token) {
        if (!available()) {
            throw new PasswordResetDeliveryException(
                    new IllegalStateException("Password reset email is not configured"));
        }
        String separator = properties.resetUrl().contains("?") ? "&" : "?";
        String link = properties.resetUrl() + separator + "token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Workflow CRM password recovery");
        message.setText("Hello, " + fullName + ".\n\nReset your password using this one-time link:\n" + link
                + "\n\nThe link expires in " + properties.tokenTtl().toMinutes() + " minutes and can be used once.");
        try {
            mail.send(message, "PASSWORD_RESET_MAIL_FROM");
        } catch (IllegalStateException exception) {
            throw new PasswordResetDeliveryException(exception);
        }
    }
}
