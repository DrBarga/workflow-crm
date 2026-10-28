package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.ObjectFile;
import java.util.List;
import java.util.UUID;

public interface ObjectFileStore {
    record Category(ObjectFile.Category value, boolean restricted, boolean active) {}

    record Row(ObjectFile file, String bucket, String key, boolean restricted, long sequence) {}

    Category category(UUID id, boolean lock);

    Row get(UUID objectId, UUID fileId);

    long snapshot(UUID objectId);

    List<Row> page(UUID objectId, boolean restrictedVisible, long after, long snapshot, int limit);

    void delete(UUID objectId, UUID fileId);
}
