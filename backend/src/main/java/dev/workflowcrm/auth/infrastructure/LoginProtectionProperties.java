package dev.workflowcrm.auth.infrastructure;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.login")
public record LoginProtectionProperties(
        @Min(1) int maxAttempts, @NotNull @DurationMin(seconds = 1) Duration lockDuration) {}
