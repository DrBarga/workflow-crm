package dev.workflowcrm.approvals.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ObjectApprovalQuery {
    record Pending(
            UUID id,
            String type,
            String requestedStatus,
            BigDecimal longitude,
            BigDecimal latitude,
            UUID initiatorId,
            UUID confirmingRoleId,
            UUID confirmingUserId,
            Instant createdAt) {}

    List<Pending> pending(UUID objectId);
}
