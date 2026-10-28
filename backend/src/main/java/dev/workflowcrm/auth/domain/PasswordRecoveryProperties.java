package dev.workflowcrm.auth.domain;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.password-recovery")
public record PasswordRecoveryProperties(Duration tokenTtl, String resetUrl, String mailFrom) {
    public PasswordRecoveryProperties {
        tokenTtl = tokenTtl == null ? Duration.ofMinutes(30) : tokenTtl;
        resetUrl = resetUrl == null ? "" : resetUrl.strip();
        mailFrom = mailFrom == null ? "" : mailFrom.strip();
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("Password reset token TTL must be positive");
        }
    }
}
