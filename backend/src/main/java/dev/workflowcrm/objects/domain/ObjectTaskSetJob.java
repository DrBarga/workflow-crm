package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.UUID;

public record ObjectTaskSetJob(
        UUID id,
        UUID objectId,
        UUID actorId,
        State state,
        UUID taskSetId,
        UUID approvalRequestId,
        String error,
        int attempts,
        Instant createdAt) {
    public enum State {
        QUEUED,
        SUCCEEDED,
        AWAITING_APPROVAL,
        FAILED
    }
}
