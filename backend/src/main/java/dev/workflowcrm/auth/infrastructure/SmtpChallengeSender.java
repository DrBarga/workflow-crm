package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.ChallengePolicy;
import dev.workflowcrm.auth.domain.EmailChallengeSender;
import dev.workflowcrm.shared.mail.SmtpMailDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
class SmtpChallengeSender implements EmailChallengeSender {
    private final SmtpMailDelivery mail;
    private final ChallengePolicy policy;
    private final String from;

    SmtpChallengeSender(
            SmtpMailDelivery mail, ChallengePolicy policy, @Value("${app.security.email.from:}") String from) {
        this.mail = mail;
        this.policy = policy;
        this.from = from.strip();
    }

    @Override
    public boolean available() {
        return mail.available(from, "AUTH_EMAIL_FROM");
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Workflow CRM login confirmation");
        message.setText("Your one-time login code: " + code + "\nIt expires in " + expiryText() + ".");
        mail.send(message, "AUTH_EMAIL_FROM");
    }

    private String expiryText() {
        long seconds = policy.ttl().toSeconds();
        if (seconds % 60 == 0) {
            return (seconds / 60) + " minutes";
        }
        return seconds + " seconds";
    }
}
