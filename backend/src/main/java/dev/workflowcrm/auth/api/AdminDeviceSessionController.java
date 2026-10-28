package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.DeviceSessionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/devices")
class AdminDeviceSessionController {

    private final DeviceSessionService sessions;

    AdminDeviceSessionController(DeviceSessionService sessions) {
        this.sessions = sessions;
    }

    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> revoke(
            @PathVariable UUID userId, @PathVariable String sessionId, @AuthenticationPrincipal Jwt jwt) {
        sessions.revokeAsAdministrator(UUID.fromString(jwt.getSubject()), userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    ResponseEntity<Void> revokeAll(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        sessions.revokeAllAsAdministrator(UUID.fromString(jwt.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }
}
