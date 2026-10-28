package dev.workflowcrm.shared.mail;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailDelivery {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailDelivery.class);
    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String username;
    private final String password;
    private final boolean authentication;

    public SmtpMailDelivery(
            ObjectProvider<JavaMailSender> senders,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authentication) {
        this.senders = senders;
        this.host = host;
        this.username = username;
        this.password = password;
        this.authentication = authentication;
    }

    public boolean available(String from, String fromVariable) {
        List<String> missing = new ArrayList<>();
        if (host.isBlank()) missing.add("SMTP_HOST");
        if (authentication && username.isBlank()) missing.add("SMTP_USERNAME");
        if (authentication && password.isBlank()) missing.add("SMTP_PASSWORD");
        if (from.isBlank()) missing.add(fromVariable);
        if (!missing.isEmpty()) {
            log.warn("SMTP delivery is not configured; missing variables: {}", String.join(", ", missing));
            return false;
        }
        if (senders.getIfAvailable() == null) {
            log.warn("SMTP delivery is unavailable; JavaMailSender is not configured");
            return false;
        }
        return true;
    }

    public void send(SimpleMailMessage message, String fromVariable) {
        String from = message.getFrom() == null ? "" : message.getFrom();
        if (!available(from, fromVariable)) {
            throw new IllegalStateException("SMTP delivery is not configured");
        }
        try {
            senders.getObject().send(message);
        } catch (MailException exception) {
            String failure = exception instanceof MailAuthenticationException
                    ? "authentication failed"
                    : exception instanceof MailSendException ? "connection or delivery failed" : "delivery failed";
            log.warn("SMTP {} ({})", failure, exception.getClass().getSimpleName());
            throw new IllegalStateException("SMTP delivery failed");
        }
    }
}
