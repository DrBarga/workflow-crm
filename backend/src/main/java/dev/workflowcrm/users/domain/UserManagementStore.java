package dev.workflowcrm.users.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface UserManagementStore {
    Page<User> search(RoleCode role, UUID regionId, UserStatus status, int page, int size);

    Optional<User> find(UUID id);

    Optional<User> lock(UUID id);

    Optional<Role> role(RoleCode code);

    Optional<Region> region(UUID id);

    User save(User user);

    void recordChange(UUID actorId, UUID userId, UserAccessState before, UserAccessState after);

    void invalidateInvitations(UUID userId, Instant invalidatedAt);

    void saveInvitation(UUID id, UUID userId, String tokenHash, Instant expiresAt);

    Optional<UserInvitation> lockInvitation(String tokenHash);

    void consumeInvitation(UUID id, Instant consumedAt);
}
