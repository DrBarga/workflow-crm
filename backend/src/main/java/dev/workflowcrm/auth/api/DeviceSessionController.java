package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AuthenticationCookieFactory;
import dev.workflowcrm.auth.domain.DeviceSessionService;
import dev.workflowcrm.auth.domain.RefreshCookieFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/devices")
class DeviceSessionController {

    private final DeviceSessionService sessions;
    private final AuthenticationCookieFactory accessCookies;
    private final RefreshCookieFactory refreshCookies;

    DeviceSessionController(
            DeviceSessionService sessions,
            AuthenticationCookieFactory accessCookies,
            RefreshCookieFactory refreshCookies) {
        this.sessions = sessions;
        this.accessCookies = accessCookies;
        this.refreshCookies = refreshCookies;
    }

    @GetMapping
    List<DeviceSessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        String currentSessionId = jwt.getClaimAsString("sid");
        return sessions.list(userId(jwt)).stream()
                .map(session -> DeviceSessionResponse.from(session, currentSessionId))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> revoke(@PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        sessions.revoke(userId(jwt), sessionId);
        var response = ResponseEntity.noContent();
        if (sessionId.equals(jwt.getClaimAsString("sid"))) {
            response.header(HttpHeaders.SET_COOKIE, accessCookies.clear(), refreshCookies.clear());
        }
        return response.build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
