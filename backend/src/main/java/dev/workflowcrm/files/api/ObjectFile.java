package dev.workflowcrm.files.api;

import java.time.Instant;
import java.util.UUID;

public record ObjectFile(
        UUID id,
        Category category,
        Author author,
        String name,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        boolean canDelete) {
    public record Category(UUID id, String code, String name) {}

    public record Author(UUID id, String name) {}
}
