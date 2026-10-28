package dev.workflowcrm.auth.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cross-origin access for the browser client. An empty origin list disables CORS entirely, which is the
 * expected state wherever the client is served from the same origin as the API.
 */
@Validated
@ConfigurationProperties("app.security.cors")
public record CorsProperties(
        @NotNull List<String> allowedOrigins,
        @NotNull List<String> allowedMethods,
        @NotNull List<String> allowedHeaders,
        @NotNull Duration preflightCacheDuration) {

    public boolean isEnabled() {
        return !allowedOrigins.isEmpty();
    }

    // Requests carry authentication cookies, so each origin must be named explicitly:
    // a wildcard would let any site act on behalf of a logged-in user.
    @AssertTrue(message = "CORS allowed origins must be explicit, wildcards are not allowed")
    public boolean isEveryOriginExplicit() {
        return allowedOrigins == null || allowedOrigins.stream().noneMatch(origin -> origin.contains("*"));
    }
}
