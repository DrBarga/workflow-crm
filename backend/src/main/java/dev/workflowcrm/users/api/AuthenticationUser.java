package dev.workflowcrm.users.api;

import java.util.UUID;

public record AuthenticationUser(
        UUID id,
        String passwordHash,
        String fullName,
        String role,
        UUID regionId,
        boolean active,
        java.util.List<UUID> regionIds) {
    public AuthenticationUser {
        regionIds = regionIds.stream().distinct().sorted().toList();
    }

    public AuthenticationUser(
            UUID id, String passwordHash, String fullName, String role, UUID regionId, boolean active) {
        this(
                id,
                passwordHash,
                fullName,
                role,
                regionId,
                active,
                regionId == null ? java.util.List.of() : java.util.List.of(regionId));
    }

    public boolean belongsTo(UUID region) {
        return region != null && regionIds.contains(region);
    }

    @Override
    public String toString() {
        return "AuthenticationUser[id=" + id + ", password hash redacted]";
    }
}
