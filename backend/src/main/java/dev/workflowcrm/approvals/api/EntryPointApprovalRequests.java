package dev.workflowcrm.approvals.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface EntryPointApprovalRequests {
    record Request(
            UUID id,
            UUID objectId,
            UUID initiatorId,
            UUID confirmingRoleId,
            UUID confirmingUserId,
            BigDecimal longitude,
            BigDecimal latitude,
            long objectVersion,
            String status,
            Instant createdAt) {}

    Request pending(UUID objectId);

    Request create(
            UUID objectId,
            UUID actorId,
            UUID roleId,
            UUID userId,
            BigDecimal longitude,
            BigDecimal latitude,
            long version);

    Request lock(UUID id);

    void finish(UUID id, UUID actorId, String decision);

    void invalidate(UUID objectId, UUID exceptRequestId);

    java.util.List<Request> cancelPending(UUID objectId, UUID actorId);
}
