package dev.workflowcrm.users.domain;

import java.util.UUID;

public record UserAccessState(RoleCode role, java.util.List<UUID> regionIds, UserStatus status) {
    public UUID regionId() {
        return regionIds.isEmpty() ? null : regionIds.getFirst();
    }

    public static UserAccessState of(User user) {
        return new UserAccessState(user.getRole().getCode(), user.getRegionIds(), user.getStatus());
    }
}
