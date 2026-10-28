package dev.workflowcrm.integrations.api;

public interface BinaryStorage {
    void deleteAllVersions(String bucket, String key);
}
