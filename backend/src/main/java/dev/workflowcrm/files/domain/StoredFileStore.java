package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.FileOwner;
import dev.workflowcrm.files.api.StoredFile;
import java.util.UUID;

public interface StoredFileStore {
    void requireCategory(UUID category);

    StoredFile create(
            FileOwner owner,
            UUID author,
            UUID category,
            FileUpload upload,
            FileUploadReservations.Reservation reservation);

    StoredFile get(FileOwner owner, UUID id);
}
