package dev.workflowcrm.auth.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotBlank String cookieName,
        @NotNull @DurationMin(seconds = 1) Duration accessTokenTtl,
        boolean secure,
        @NotBlank String refreshCookieName,
        @NotNull @DurationMin(seconds = 1) Duration refreshTokenTtl) {

    @AssertTrue(message = "Refresh token TTL must be greater than access token TTL")
    public boolean isRefreshTtlLongerThanAccess() {
        return refreshTokenTtl == null || accessTokenTtl == null || refreshTokenTtl.compareTo(accessTokenTtl) > 0;
    }

    @AssertTrue(message = "Access and refresh cookie names must be different")
    public boolean isCookieNameDistinct() {
        return cookieName == null || !cookieName.equals(refreshCookieName);
    }
}
