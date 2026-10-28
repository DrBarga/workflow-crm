package dev.workflowcrm.users.api;

import java.util.Optional;
import java.util.UUID;

public interface ObjectAssignmentQuery {
    record Member(
            UUID id,
            String role,
            UUID regionId,
            boolean active,
            String fullName,
            UUID roleId,
            java.util.List<UUID> regionIds) {
        public Member {
            var assigned = new java.util.LinkedHashSet<>(regionIds);
            if (regionId != null) assigned.add(regionId);
            regionIds = java.util.List.copyOf(assigned);
        }

        public Member(UUID id, String role, UUID regionId, boolean active, String fullName, UUID roleId) {
            this(id, role, regionId, active, fullName, roleId, java.util.List.of());
        }

        public boolean belongsTo(UUID region) {
            return region != null && regionIds.contains(region);
        }

        public Member(UUID id, String role, UUID regionId, boolean active, String fullName) {
            this(id, role, regionId, active, fullName, null);
        }

        public Member(UUID id, String role, UUID regionId, boolean active) {
            this(id, role, regionId, active, null, null);
        }
    }

    Optional<Member> member(UUID id);

    boolean regionExists(UUID id);

    boolean regionContains(UUID parentId, UUID regionId);

    java.util.List<UUID> regionTree(UUID rootId);

    Optional<UUID> regionByGeocoding(String code, String name);

    java.util.List<UUID> activeMembers(String role);

    java.util.List<UUID> activeMembers(UUID roleId);

    /** Active assignment candidates; a null role includes all roles. */
    java.util.List<Member> assignmentCandidates(UUID roleId);

    Optional<AssignmentRole> assignmentRole(UUID roleId);

    record AssignmentRole(UUID id, String code, String name) {}
}
