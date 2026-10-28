package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class HttpOnlyJwtCookieFactory implements AuthenticationCookieFactory {

    private final JwtProperties properties;

    HttpOnlyJwtCookieFactory(JwtProperties properties) {
        this.properties = properties;
    }

    @Override
    public String create(String token) {
        return cookie(token).maxAge(properties.accessTokenTtl()).build().toString();
    }

    @Override
    public String clear() {
        return cookie("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String token) {
        return ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path("/");
    }
}
