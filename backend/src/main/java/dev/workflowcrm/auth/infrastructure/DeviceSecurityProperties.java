package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.ConfirmationMode;
import dev.workflowcrm.auth.domain.DevicePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.device")
public record DeviceSecurityProperties(Duration trustDuration, ConfirmationMode confirmationMode, String cookieName)
        implements DevicePolicy {
    public DeviceSecurityProperties {
        if (trustDuration == null || trustDuration.isNegative() || trustDuration.isZero()) {
            throw new IllegalArgumentException("Device trust duration must be positive");
        }
        if (confirmationMode == null || cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("Device security settings are incomplete");
        }
    }
}
