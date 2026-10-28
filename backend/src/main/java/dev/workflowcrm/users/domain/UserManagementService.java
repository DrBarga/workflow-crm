package dev.workflowcrm.users.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserManagementService {
    private final UserManagementStore store;
    private final CurrentUser currentUser;
    private final InitialCredential credentials;
    private final UserAccessEditor access;
    private final UserInvitationService invitations;

    public UserManagementService(
            UserManagementStore store,
            CurrentUser currentUser,
            InitialCredential credentials,
            UserAccessEditor access,
            UserInvitationService invitations) {
        this.store = store;
        this.currentUser = currentUser;
        this.credentials = credentials;
        this.access = access;
        this.invitations = invitations;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(RoleCode role, UUID regionId, UserStatus status, int page, int size) {
        requireAdministrator();
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0..100000 and size 1..100");
        }
        return store.search(role, regionId, status, page, size).map(UserView::of);
    }

    @Transactional(readOnly = true)
    public UserView get(UUID id) {
        requireAdministrator();
        return UserView.of(store.find(id).orElseThrow(UserManagementService::notFound));
    }

    public UserView create(String email, String fullName, RoleCode role, java.util.List<UUID> regionIds) {
        UUID actorId = requireAdministrator();
        Role assignedRole = requireRole(role);
        var assignedRegions = access.assignments(role, regionIds);
        User user = store.save(new User(
                email,
                credentials.unusablePasswordHash(),
                fullName.strip(),
                assignedRole,
                assignedRegions,
                UserStatus.DISABLED));
        store.recordChange(actorId, user.getId(), null, UserAccessState.of(user));
        invitations.issue(user);
        return UserView.of(user);
    }

    public UserView update(
            UUID id,
            RoleCode role,
            java.util.List<UUID> regionIds,
            boolean regionProvided,
            UserStatus status,
            Long version) {
        return access.update(requireAdministrator(), id, role, regionIds, regionProvided, status, version);
    }

    public void resendPasswordInvitation(UUID id) {
        requireAdministrator();
        User user = store.lock(id).orElseThrow(UserManagementService::notFound);
        if (user.getStatus() != UserStatus.DISABLED || !user.isPasswordSetupRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is not awaiting an invitation");
        }
        invitations.resend(user);
    }

    private UUID requireAdministrator() {
        UUID actorId = currentUser.id();
        User actor = store.find(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (actor.getStatus() != UserStatus.ACTIVE || actor.getRole().getCode() != RoleCode.SUPERADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return actorId;
    }

    private Role requireRole(RoleCode code) {
        return store.role(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role"));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
}
