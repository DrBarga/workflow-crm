package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import dev.workflowcrm.auth.domain.RefreshService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class RefreshController {

    private final RefreshService refreshService;
    private final AuthenticationCookieFactory cookieFactory;
    private final RefreshCookieFactory refreshCookieFactory;

    RefreshController(
            RefreshService refreshService,
            AuthenticationCookieFactory cookieFactory,
            RefreshCookieFactory refreshCookieFactory) {
        this.refreshService = refreshService;
        this.cookieFactory = cookieFactory;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(
            @CookieValue(name = "${app.security.jwt.refresh-cookie-name}", required = false) String refreshToken) {
        var pair = refreshService.refresh(refreshToken);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.create(pair.accessToken()),
                        refreshCookieFactory.create(pair.refreshToken(), pair.refreshExpiresAt()))
                .build();
    }
}
