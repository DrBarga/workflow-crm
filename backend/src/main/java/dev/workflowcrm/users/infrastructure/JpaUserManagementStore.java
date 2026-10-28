package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.Region;
import dev.workflowcrm.users.domain.Role;
import dev.workflowcrm.users.domain.RoleCode;
import dev.workflowcrm.users.domain.User;
import dev.workflowcrm.users.domain.UserAccessState;
import dev.workflowcrm.users.domain.UserInvitation;
import dev.workflowcrm.users.domain.UserManagementStore;
import dev.workflowcrm.users.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class JpaUserManagementStore implements UserManagementStore {
    private final UserRepository users;
    private final EntityManager entities;
    private final JpaUserAccessHistory history;
    private final JpaUserInvitationStore invitations;

    JpaUserManagementStore(
            UserRepository users,
            EntityManager entities,
            JpaUserAccessHistory history,
            JpaUserInvitationStore invitations) {
        this.users = users;
        this.entities = entities;
        this.history = history;
        this.invitations = invitations;
    }

    public Page<User> search(RoleCode role, UUID regionId, UserStatus status, int page, int size) {
        Specification<User> filter = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (role != null) predicates.add(builder.equal(root.get("role").get("code"), role));
            if (regionId != null) {
                var assigned = query.subquery(java.util.UUID.class);
                var member = assigned.from(User.class);
                assigned.select(member.get("id"))
                        .where(
                                builder.equal(member.get("id"), root.get("id")),
                                builder.equal(member.join("regions").get("id"), regionId));
                predicates.add(builder.exists(assigned));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return users.findAll(
                filter,
                PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
    }

    public Optional<User> find(UUID id) {
        return users.findById(id);
    }

    public Optional<User> lock(UUID id) {
        return users.findForUpdate(id);
    }

    public Optional<Role> role(RoleCode code) {
        return entities.createQuery("select r from Role r where r.code = :code", Role.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst();
    }

    public Optional<Region> region(UUID id) {
        return Optional.ofNullable(entities.find(Region.class, id));
    }

    public User save(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "ux_users_email_lower".equals(constraint.getConstraintName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
                }
            }
            throw exception;
        }
    }

    public void recordChange(UUID actorId, UUID userId, UserAccessState before, UserAccessState after) {
        history.recordChange(actorId, userId, before, after);
    }

    public void invalidateInvitations(UUID userId, Instant invalidatedAt) {
        invitations.invalidateInvitations(userId, invalidatedAt);
    }

    public void saveInvitation(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        invitations.saveInvitation(id, userId, tokenHash, expiresAt);
    }

    public Optional<UserInvitation> lockInvitation(String tokenHash) {
        return invitations.lockInvitation(tokenHash);
    }

    public void consumeInvitation(UUID id, Instant consumedAt) {
        invitations.consumeInvitation(id, consumedAt);
    }
}
