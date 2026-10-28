package dev.workflowcrm.files.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

public interface FileStorage {
    record Download(String url, Instant expiresAt) {}

    StoredFile upload(
            FileOwner owner,
            UUID author,
            UUID category,
            String name,
            InputStream content,
            Consumer<StoredFile> onStored)
            throws IOException;

    Download download(FileOwner owner, UUID fileId);
}
