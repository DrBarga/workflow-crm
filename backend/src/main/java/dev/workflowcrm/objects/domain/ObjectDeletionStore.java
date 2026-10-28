package dev.workflowcrm.objects.domain;

import dev.workflowcrm.files.api.ObjectFileStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectDeletionStore {
    void lockObject(UUID objectId);

    Optional<ObjectDeletion> pending(UUID objectId);

    ObjectDeletion create(UUID objectId, UUID actorId, List<ObjectFileStorage.File> files);

    ObjectDeletion get(UUID operationId);

    Optional<ObjectDeletion> claimNext();

    List<ObjectFileStorage.File> files(UUID operationId);

    void complete(ObjectDeletion operation);

    void failed(UUID operationId, String error);
}
