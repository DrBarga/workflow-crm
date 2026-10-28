package dev.workflowcrm.tasks.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Task(
        UUID id,
        String name,
        UUID objectId,
        LocalDate deadline,
        UUID assigneeId,
        UUID authorId,
        TaskAuthorType authorType,
        boolean needsReview,
        UUID resultRecipientId,
        TaskStatus status,
        boolean manuallyPaused,
        Instant createdAt,
        Instant completedAt,
        UUID sourceItemId,
        UUID templateVersionId,
        UUID taskSetId,
        long version) {
    public Task {
        Objects.requireNonNull(id);
        Objects.requireNonNull(objectId);
        Objects.requireNonNull(deadline);
        Objects.requireNonNull(assigneeId);
        Objects.requireNonNull(authorId);
        Objects.requireNonNull(authorType);
        Objects.requireNonNull(status);
        if (manuallyPaused && status != TaskStatus.PAUSED)
            throw new IllegalArgumentException("Manual pause requires paused task status");
        Objects.requireNonNull(createdAt);
        if (name == null || name.isBlank() || name.length() > 250)
            throw new IllegalArgumentException("Invalid task name");
        if (version < 0) throw new IllegalArgumentException("Negative task version");
        if ((sourceItemId == null) != (templateVersionId == null) || templateVersionId != null && taskSetId == null)
            throw new IllegalArgumentException("Incomplete task template source");
        if (completedAt != null && (status != TaskStatus.DONE || completedAt.isBefore(createdAt)))
            throw new IllegalArgumentException("Invalid task completion time");
    }
}
