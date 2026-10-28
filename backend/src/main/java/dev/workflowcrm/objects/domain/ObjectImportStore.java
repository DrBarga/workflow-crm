package dev.workflowcrm.objects.domain;

import java.util.*;

public interface ObjectImportStore {
    ObjectImport.View enqueue(UUID actor, String hash, List<ObjectImport.Input> rows);

    Optional<ObjectImport.Job> claim();

    ObjectImport.View view(UUID id);

    List<ObjectImport.Input> inputs(UUID id);

    ObjectImport.Report report(UUID id, int offset, int limit);

    void row(UUID id, ObjectImport.Row row);

    void complete(UUID id);

    void failed(UUID id, String reason, boolean retry);
}
