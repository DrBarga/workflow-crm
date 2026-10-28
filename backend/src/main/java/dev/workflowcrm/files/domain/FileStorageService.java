package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.*;
import dev.workflowcrm.integrations.api.BinaryFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FileStorageService implements FileStorage {
    private final FileProperties properties;
    private final FileUploadValidation validation;
    private final FileUploadReservations reservations;
    private final StoredFileStore files;
    private final BinaryFileStorage storage;

    FileStorageService(
            FileProperties properties,
            FileUploadValidation validation,
            FileUploadReservations reservations,
            StoredFileStore files,
            BinaryFileStorage storage) {
        this.properties = properties;
        this.validation = validation;
        this.reservations = reservations;
        this.files = files;
        this.storage = storage;
    }

    @Transactional(rollbackFor = IOException.class)
    public StoredFile upload(
            FileOwner owner,
            UUID author,
            UUID category,
            String name,
            InputStream content,
            Consumer<StoredFile> onStored)
            throws IOException {
        Objects.requireNonNull(owner, "File owner required");
        Objects.requireNonNull(author, "File author required");
        Objects.requireNonNull(onStored, "File publication callback required");
        files.requireCategory(category);
        var upload = validation.validate(name, content);
        var reservation = reservations.reserve(properties.bucket(owner.type()), owner);
        reservations.lock(reservation);
        storage.upload(
                reservation.bucket(),
                reservation.key(),
                new ByteArrayInputStream(upload.content()),
                upload.content().length,
                upload.contentType());
        var file = files.create(owner, author, category, upload, reservation);
        onStored.accept(file);
        reservations.complete(reservation.id());
        return file;
    }

    @Transactional(readOnly = true)
    public Download download(FileOwner owner, UUID id) {
        var file = files.get(owner, id);
        var url = storage.downloadLink(file.bucket(), file.key(), file.name(), properties.linkTtl());
        var expires = Instant.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(properties.linkTtl().toSeconds());
        return new Download(url, expires);
    }
}
