package dev.workflowcrm.users.api;

import dev.workflowcrm.users.domain.RoleCode;
import dev.workflowcrm.users.domain.UserLookupService;
import dev.workflowcrm.users.domain.UserManagementService;
import dev.workflowcrm.users.domain.UserStatus;
import dev.workflowcrm.users.domain.UserView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {
    private final UserManagementService users;
    private final UserLookupService lookups;

    UserController(UserManagementService users, UserLookupService lookups) {
        this.users = users;
        this.lookups = lookups;
    }

    @GetMapping
    UserPage<?> list(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) UUID regionId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lookups.administrator()
                ? UserPage.of(users.list(role, regionId, status, page, size))
                : UserPage.of(lookups.managers(role, regionId, status, page, size));
    }

    @GetMapping("/{id}")
    UserView get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        UserView user = users.create(request.email(), request.fullName(), request.role(), request.regionIds());
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @PatchMapping("/{id}")
    UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(
                id, request.role(), request.regionIds(), request.regionProvided(), request.status(), request.version());
    }

    @PostMapping("/{id}/invitation")
    ResponseEntity<Void> resendInvitation(@PathVariable UUID id) {
        users.resendPasswordInvitation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/password-invitation")
    ResponseEntity<Void> resendPasswordInvitation(@PathVariable UUID id) {
        users.resendPasswordInvitation(id);
        return ResponseEntity.noContent().build();
    }
}
