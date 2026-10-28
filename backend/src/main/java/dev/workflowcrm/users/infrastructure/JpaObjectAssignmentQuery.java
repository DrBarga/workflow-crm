package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import dev.workflowcrm.users.domain.Region;
import dev.workflowcrm.users.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaObjectAssignmentQuery implements ObjectAssignmentQuery {
    private final UserRepository users;
    private final EntityManager entities;
    private final JdbcUserRegions regions;

    JpaObjectAssignmentQuery(UserRepository users, EntityManager entities, JdbcUserRegions regions) {
        this.users = users;
        this.entities = entities;
        this.regions = regions;
    }

    public Optional<Member> member(UUID id) {
        return users.findById(id).map(u -> {
            var assigned = regions.assigned(id);
            return new Member(
                    u.getId(),
                    u.getRole().getCode().name(),
                    assigned.isEmpty() ? null : assigned.getFirst(),
                    u.getStatus() == UserStatus.ACTIVE,
                    u.getFullName(),
                    u.getRole().getId(),
                    assigned);
        });
    }

    public Optional<AssignmentRole> assignmentRole(UUID id) {
        var role = id == null ? null : entities.find(dev.workflowcrm.users.domain.Role.class, id);
        return role == null
                ? Optional.empty()
                : Optional.of(new AssignmentRole(id, role.getCode().name(), role.getName()));
    }

    public boolean regionExists(UUID id) {
        return entities.find(Region.class, id) != null;
    }

    public boolean regionContains(UUID parentId, UUID regionId) {
        return parentId != null && parentId.equals(regionId) && regionExists(regionId);
    }

    public java.util.List<UUID> regionTree(UUID rootId) {
        return rootId != null && regionExists(rootId) ? java.util.List.of(rootId) : java.util.List.of();
    }

    public Optional<UUID> regionByGeocoding(String code, String name) {
        var matches = entities.createQuery(
                        "select r.id from Region r where upper(r.code) = upper(:code) or lower(r.name) = lower(:name)",
                        UUID.class)
                .setParameter("code", code)
                .setParameter("name", name)
                .getResultList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public java.util.List<UUID> activeMembers(String role) {
        return entities.createQuery(
                        "select u.id from User u where u.status = :status and u.role.code = :role", UUID.class)
                .setParameter("status", UserStatus.ACTIVE)
                .setParameter("role", dev.workflowcrm.users.domain.RoleCode.valueOf(role))
                .getResultList();
    }

    public java.util.List<UUID> activeMembers(UUID roleId) {
        return entities.createQuery("select u.id from User u where u.status=:status and u.role.id=:role", UUID.class)
                .setParameter("status", UserStatus.ACTIVE)
                .setParameter("role", roleId)
                .getResultList();
    }

    public java.util.List<Member> assignmentCandidates(UUID roleId) {
        var query = entities.createQuery(
                "select distinct u from User u join fetch u.role left join fetch u.regions " + "where u.status=:status"
                        + (roleId == null ? "" : " and u.role.id=:roleId"),
                dev.workflowcrm.users.domain.User.class);
        query.setParameter("status", UserStatus.ACTIVE);
        if (roleId != null) query.setParameter("roleId", roleId);
        return query.getResultList().stream()
                .map(user -> {
                    var assigned = user.getRegionIds();
                    return new Member(
                            user.getId(),
                            user.getRole().getCode().name(),
                            assigned.isEmpty() ? null : assigned.getFirst(),
                            true,
                            user.getFullName(),
                            user.getRole().getId(),
                            assigned);
                })
                .toList();
    }
}
