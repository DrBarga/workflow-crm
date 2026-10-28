package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ObjectExportStore {
    void enqueue(ObjectExport.Job job);

    ObjectExport.Job get(UUID id, UUID actor);

    Optional<ObjectExport.Job> claim(Instant now);

    Optional<ObjectExport.Job> expired(Instant now);

    void ready(UUID id, long rows, long bytes);

    void failed(UUID id, String error, boolean retry);

    void expire(UUID id);
}
