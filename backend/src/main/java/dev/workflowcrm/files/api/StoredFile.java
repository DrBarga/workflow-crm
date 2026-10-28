package dev.workflowcrm.files.api;

import java.time.Instant;
import java.util.UUID;

public record StoredFile(
        UUID id,
        FileOwner owner,
        UUID authorId,
        UUID categoryId,
        String bucket,
        String key,
        String name,
        String contentType,
        long sizeBytes,
        Instant createdAt) {}
