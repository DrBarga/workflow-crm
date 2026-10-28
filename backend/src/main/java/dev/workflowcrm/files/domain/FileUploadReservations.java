package dev.workflowcrm.files.domain;

import java.util.Optional;
import java.util.UUID;

public interface FileUploadReservations {
    record Reservation(UUID id, String bucket, String key) {}

    Reservation reserve(String bucket, dev.workflowcrm.files.api.FileOwner owner);

    void lock(Reservation reservation);

    void complete(UUID id);

    Optional<Reservation> expired();

    void retry(UUID id);
}
