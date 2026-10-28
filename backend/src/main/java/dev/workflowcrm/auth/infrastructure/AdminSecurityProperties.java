package dev.workflowcrm.auth.infrastructure;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.administration")
public record AdminSecurityProperties(@Min(1) int maxActions, @NotNull @DurationMin(seconds = 1) Duration rateWindow) {}
