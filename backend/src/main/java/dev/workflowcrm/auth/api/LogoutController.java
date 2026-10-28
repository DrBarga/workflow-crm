package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import dev.workflowcrm.auth.domain.LogoutService;
import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class LogoutController {

    private final LogoutService logoutService;
    private final AuthenticationCookieFactory cookieFactory;
    private final RefreshCookieFactory refreshCookieFactory;

    LogoutController(
            LogoutService logoutService,
            AuthenticationCookieFactory cookieFactory,
            RefreshCookieFactory refreshCookieFactory) {
        this.logoutService = logoutService;
        this.cookieFactory = cookieFactory;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt token) {
        logoutService.logout(token.getId(), token.getExpiresAt(), token.getClaimAsString("sid"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear(), refreshCookieFactory.clear())
                .build();
    }
}
