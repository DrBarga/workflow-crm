package dev.workflowcrm.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CorsConfigurationTest {

    private static final String CLIENT_ORIGIN = "http://localhost:3000";

    @Test
    void rejectsWildcardOrigins() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(corsPropertiesAllowing(List.of("*")))).hasSize(1);
            assertThat(validator.validate(corsPropertiesAllowing(List.of("http://*.example.com"))))
                    .hasSize(1);
            assertThat(validator.validate(corsPropertiesAllowing(List.of(CLIENT_ORIGIN))))
                    .isEmpty();
        }
    }

    @Test
    void emptyOriginListLeavesCorsDisabled() {
        var source = new SecurityConfiguration().corsConfigurationSource(corsPropertiesAllowing(List.of()));

        assertThat(source.getCorsConfiguration(requestFromClient())).isNull();
    }

    @Test
    void configuredOriginIsAllowedWithCookies() {
        var source =
                new SecurityConfiguration().corsConfigurationSource(corsPropertiesAllowing(List.of(CLIENT_ORIGIN)));

        var configuration = source.getCorsConfiguration(requestFromClient());

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin(CLIENT_ORIGIN)).isEqualTo(CLIENT_ORIGIN);
        assertThat(configuration.checkOrigin("http://attacker.example")).isNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    private static CorsProperties corsPropertiesAllowing(List<String> allowedOrigins) {
        return new CorsProperties(
                allowedOrigins, List.of("GET", "POST"), List.of("Content-Type", "X-XSRF-TOKEN"), Duration.ofHours(1));
    }

    private static MockHttpServletRequest requestFromClient() {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Origin", CLIENT_ORIGIN);
        return request;
    }
}
