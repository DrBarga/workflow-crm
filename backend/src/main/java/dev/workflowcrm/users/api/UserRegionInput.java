package dev.workflowcrm.users.api;

import java.util.List;
import java.util.UUID;

final class UserRegionInput {
    private UserRegionInput() {}

    static List<UUID> resolve(UUID regionId, List<UUID> regionIds) {
        if (regionId != null && regionIds != null) throw new IllegalArgumentException("Use regionIds or regionId");
        if (regionIds == null) return regionId == null ? List.of() : List.of(regionId);
        if (regionIds.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Invalid regionIds");
        return regionIds.stream().distinct().sorted().toList();
    }
}
