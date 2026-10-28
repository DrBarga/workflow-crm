package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.ChallengePolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.challenge")
public record ChallengeProperties(
        @NotNull @DurationMin(seconds = 30) @DurationMax(minutes = 30) Duration ttl,
        @Min(1) @Max(10) int maxEmailAttempts)
        implements ChallengePolicy {}
