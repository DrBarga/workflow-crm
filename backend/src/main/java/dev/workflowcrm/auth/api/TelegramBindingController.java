package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.LoginFlowService;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class TelegramBindingController {
    private static final Set<String> SUPERADMIN = Set.of("SUPERADMIN");

    private final LoginFlowService loginFlow;
    private final ResourceAccessGuard access;

    TelegramBindingController(LoginFlowService loginFlow, ResourceAccessGuard access) {
        this.loginFlow = loginFlow;
        this.access = access;
    }

    @PostMapping("/auth/telegram/link")
    ResponseEntity<LinkResponse> link(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new LinkResponse(loginFlow.bindingLink(UUID.fromString(jwt.getSubject()))));
    }

    @GetMapping("/auth/telegram")
    ResponseEntity<BindingStatus> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new BindingStatus(loginFlow.linked(UUID.fromString(jwt.getSubject()))));
    }

    @DeleteMapping("/auth/telegram")
    ResponseEntity<Void> unbindSelf(@AuthenticationPrincipal Jwt jwt) {
        loginFlow.unbind(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/telegram")
    ResponseEntity<Void> unbindUser(@PathVariable UUID id) {
        requireSuperadmin(id);
        loginFlow.unbind(id);
        return ResponseEntity.noContent().build();
    }

    private void requireSuperadmin(UUID targetUserId) {
        access.requireAccess(new ResourceScope(targetUserId, null), SUPERADMIN, AccessScope.NETWORK);
    }

    record LinkResponse(String link) {}

    record BindingStatus(boolean linked) {}
}
