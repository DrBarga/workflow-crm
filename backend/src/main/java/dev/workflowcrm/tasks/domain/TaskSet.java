package dev.workflowcrm.tasks.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskSet(
        UUID id,
        UUID objectId,
        UUID templateVersionId,
        UUID triggerStatusId,
        Instant createdAt,
        TaskSetState state,
        long version) {
    public TaskSet {
        Objects.requireNonNull(id);
        Objects.requireNonNull(objectId);
        Objects.requireNonNull(triggerStatusId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(state);
        if (version < 0) throw new IllegalArgumentException("Negative task set version");
    }
}
