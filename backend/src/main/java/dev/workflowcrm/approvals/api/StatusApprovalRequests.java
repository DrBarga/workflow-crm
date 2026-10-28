package dev.workflowcrm.approvals.api;

import java.util.List;
import java.util.UUID;

public interface StatusApprovalRequests {
    record Request(
            UUID id,
            UUID objectId,
            UUID initiatorId,
            UUID confirmingRoleId,
            UUID transitionId,
            long objectVersion,
            String command,
            String status) {}

    Request create(UUID objectId, UUID initiatorId, UUID roleId, UUID transitionId, long version, String command);

    Request lock(UUID id);

    void finish(UUID id, UUID actorId, String decision);

    void invalidate(UUID objectId, UUID exceptRequestId);

    List<Request> cancelPending(UUID objectId, UUID actorId, UUID exceptRequestId);
}
