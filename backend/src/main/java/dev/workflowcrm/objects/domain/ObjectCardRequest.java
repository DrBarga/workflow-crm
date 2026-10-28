package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.UUID;

public record ObjectCardRequest(
        UUID id,
        String type,
        Requested requested,
        UUID initiatorId,
        UUID confirmingRoleId,
        UUID confirmingUserId,
        Instant requestedAt) {
    public record Requested(String status, CreatedObjectView.Coordinates entrance) {}
}
