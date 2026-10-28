package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.shared.mail.SmtpMailDelivery;
import dev.workflowcrm.users.domain.UserInvitationSender;
import dev.workflowcrm.users.domain.UserProvisioningProperties;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class SmtpUserInvitationSender implements UserInvitationSender {
    private final SmtpMailDelivery mail;
    private final UserProvisioningProperties properties;

    SmtpUserInvitationSender(SmtpMailDelivery mail, UserProvisioningProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public void sendPasswordSetup(String email, String fullName, String token) {
        if (properties.invitationUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "User invitation email is not configured");
        }
        String separator = properties.invitationUrl().contains("?") ? "&" : "?";
        String link = properties.invitationUrl() + separator + "token=" + token;
        send(
                email,
                "Workflow CRM account invitation",
                "Hello, " + fullName + ".\n\nSet your password using this one-time link:\n" + link
                        + "\n\nThe browser you use to set your password will be trusted for your first sign-in."
                        + "\nThe link is time-limited and can be used once.");
    }

    private void send(String email, String subject, String text) {
        if (!mail.available(properties.mailFrom(), "USER_INVITATION_MAIL_FROM")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "User invitation email is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mail.send(message, "USER_INVITATION_MAIL_FROM");
    }
}
