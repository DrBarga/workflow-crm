package dev.workflowcrm.integrations.infrastructure;

import dev.workflowcrm.integrations.api.*;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.stereotype.Component;

@Component
class MinioBinaryStorage implements BinaryStorage {
    private final MinioClient client;

    MinioBinaryStorage(MinioClient client) {
        this.client = client;
    }

    public void deleteAllVersions(String bucket, String key) {
        try {
            var listing = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(key)
                    .recursive(true)
                    .includeVersions(true)
                    .build();
            for (var result : client.listObjects(listing)) {
                var item = result.get();
                if (key.equals(item.objectName()))
                    client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .versionId(item.versionId())
                            .build());
            }
            for (var result : client.listObjects(listing))
                if (key.equals(result.get().objectName())) throw new IllegalStateException("Stored file still exists");
        } catch (ErrorResponseException e) {
            if (!"NoSuchBucket".equals(e.errorResponse().code())) throw new BinaryStorageException(e);
        } catch (Exception e) {
            throw new BinaryStorageException(e);
        }
    }
}
