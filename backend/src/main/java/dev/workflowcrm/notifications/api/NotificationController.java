package dev.workflowcrm.notifications.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NotificationController {
    private final NotificationQuery notifications;

    NotificationController(NotificationQuery notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/notifications")
    ResponseEntity<List<NotificationQuery.Notification>> latest(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(notifications.latest(
                        UUID.fromString(authentication.getToken().getSubject())));
    }
}
