package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class HttpOnlyRefreshCookieFactory implements RefreshCookieFactory {

    private final JwtProperties properties;
    private final Clock clock;

    HttpOnlyRefreshCookieFactory(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String create(String token) {
        return cookie(token).maxAge(properties.refreshTokenTtl()).build().toString();
    }

    @Override
    public String create(String token, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(clock.instant(), expiresAt).getSeconds());
        return cookie(token).maxAge(seconds).build().toString();
    }

    @Override
    public String clear() {
        return cookie("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String token) {
        return ResponseCookie.from(properties.refreshCookieName(), token)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path("/api/auth");
    }
}
