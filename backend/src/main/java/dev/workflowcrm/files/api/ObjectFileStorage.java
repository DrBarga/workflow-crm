package dev.workflowcrm.files.api;

import java.util.List;
import java.util.UUID;

public interface ObjectFileStorage {
    record File(UUID id, String bucket, String key) {}

    List<File> files(UUID objectId);

    void purge(File file);
}
