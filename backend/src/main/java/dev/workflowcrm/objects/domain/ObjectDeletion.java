package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.UUID;

public record ObjectDeletion(
        UUID id,
        UUID objectId,
        UUID actorId,
        String state,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant completedAt) {}
