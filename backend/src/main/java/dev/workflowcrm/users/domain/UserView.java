package dev.workflowcrm.users.domain;

import java.time.Instant;
import java.util.UUID;

public record UserView(
        UUID id,
        String email,
        String fullName,
        RoleCode role,
        UUID regionId,
        UserStatus status,
        boolean passwordSet,
        boolean telegramLinked,
        Instant createdAt,
        Instant updatedAt,
        long version,
        java.util.List<UUID> regionIds) {
    public static UserView of(User user) {
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getCode(),
                user.getRegionIds().isEmpty() ? null : user.getRegionIds().getFirst(),
                user.getStatus(),
                !user.isPasswordSetupRequired(),
                user.getTelegramChatId() != null,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion(),
                user.getRegionIds());
    }
}
