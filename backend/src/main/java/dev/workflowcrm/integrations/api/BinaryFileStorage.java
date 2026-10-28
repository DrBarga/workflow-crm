package dev.workflowcrm.integrations.api;

import java.io.InputStream;
import java.time.Duration;

public interface BinaryFileStorage {
    void upload(String bucket, String key, InputStream input, long size, String contentType);

    String downloadLink(String bucket, String key, String name, Duration lifetime);
}
