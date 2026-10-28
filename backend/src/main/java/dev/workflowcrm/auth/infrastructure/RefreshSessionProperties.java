package dev.workflowcrm.auth.infrastructure;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.refresh-session")
public record RefreshSessionProperties(
        @DefaultValue("30d") @NotNull @DurationMin(seconds = 1) Duration absoluteTtl,
        @DefaultValue("30s") @NotNull @DurationMin(seconds = 1) Duration gracePeriod) {}
