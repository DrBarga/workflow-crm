package dev.workflowcrm.users.domain;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserAccessEditor {
    private final UserManagementStore store;
    private final UserRegionPolicy regions;

    UserAccessEditor(UserManagementStore store, UserRegionPolicy regions) {
        this.store = store;
        this.regions = regions;
    }

    UserView update(
            UUID actorId,
            UUID id,
            RoleCode role,
            java.util.List<UUID> regionIds,
            boolean regionProvided,
            UserStatus status,
            Long version) {
        if (role == null && !regionProvided && status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide role, regionId or status");
        }
        User user = store.lock(id).orElseThrow(UserAccessEditor::notFound);
        if (version != null && version != user.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User has changed; reload before editing");
        }
        if (actorId.equals(id) && (status == UserStatus.DISABLED || (role != null && role != RoleCode.SUPERADMIN))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You cannot deactivate yourself or remove your own administrator role");
        }
        if (status == UserStatus.ACTIVE && user.isPasswordSetupRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User must set a password using the invitation");
        }
        UserAccessState before = UserAccessState.of(user);
        Role assignedRole = role == null ? user.getRole() : requireRole(role);
        var assignedRegions = regionProvided ? assignments(assignedRole.getCode(), regionIds) : user.getRegions();
        regions.validate(
                assignedRole.getCode(),
                assignedRegions.stream().map(Region::getId).toList());
        user.changeAccess(assignedRole, assignedRegions, status == null ? user.getStatus() : status);
        UserAccessState after = UserAccessState.of(user);
        if (!before.equals(after)) {
            store.save(user);
            store.recordChange(actorId, id, before, after);
        }
        return UserView.of(user);
    }

    java.util.Set<Region> assignments(RoleCode role, java.util.List<UUID> ids) {
        regions.validate(role, ids);
        return ids.stream()
                .map(id -> store.region(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown region")))
                .collect(java.util.stream.Collectors.toSet());
    }

    private Role requireRole(RoleCode code) {
        return store.role(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role"));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
}
