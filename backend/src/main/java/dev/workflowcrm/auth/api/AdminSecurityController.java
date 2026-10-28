package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.AdminSecurityService;
import dev.workflowcrm.auth.domain.LoginLock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}")
class AdminSecurityController {
    private final AdminSecurityService security;

    AdminSecurityController(AdminSecurityService security) {
        this.security = security;
    }

    @GetMapping("/blocked-devices")
    List<BlockedDeviceResponse> blockedDevices(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        return security.blockedDevices(UUID.fromString(jwt.getSubject()), userId).stream()
                .map(device -> new BlockedDeviceResponse(
                        device.id(), device.userAgent(), device.ipAddress(), device.blockedAt()))
                .toList();
    }

    @PostMapping("/blocked-devices/{blockId}/unblock")
    ResponseEntity<Void> unblockDevice(
            @PathVariable UUID userId, @PathVariable UUID blockId, @AuthenticationPrincipal Jwt jwt) {
        security.unblockDevice(UUID.fromString(jwt.getSubject()), userId, blockId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-lock")
    LoginLock loginLock(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        return security.loginLock(UUID.fromString(jwt.getSubject()), userId);
    }

    @PostMapping("/login-lock/unlock")
    ResponseEntity<Void> unlockLogin(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        security.unlockLogin(UUID.fromString(jwt.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }

    private record BlockedDeviceResponse(UUID id, String userAgent, String ipAddress, Instant blockedAt) {}
}
