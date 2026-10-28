package dev.workflowcrm.audit.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectDuplicateAudit {
    Optional<String> cancellationHash(UUID actor, String key);

    void record(UUID actor, String key, String hash, UUID object, long total, List<UUID> nearest);
}
