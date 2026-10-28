package dev.workflowcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void requiresDistinctCookiesAndLongerRefreshLifetime() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var invalid = new JwtProperties(
                    "a".repeat(32),
                    "workflow",
                    "access",
                    Duration.ofMinutes(15),
                    true,
                    "access",
                    Duration.ofMinutes(15));
            assertThat(validator.validate(invalid)).hasSize(2);
            var valid = new JwtProperties(
                    "a".repeat(32), "workflow", "access", Duration.ofMinutes(15), true, "refresh", Duration.ofDays(7));
            assertThat(validator.validate(valid)).isEmpty();
        }
    }
}
