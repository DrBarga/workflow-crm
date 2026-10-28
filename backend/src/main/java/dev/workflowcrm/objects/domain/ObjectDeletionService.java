package dev.workflowcrm.objects.domain;

import dev.workflowcrm.files.api.ObjectFileStorage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectDeletionService {
    private final ObjectDeletionAccess access;
    private final ObjectDeletionStore store;
    private final ObjectFileStorage files;

    ObjectDeletionService(ObjectDeletionAccess access, ObjectDeletionStore store, ObjectFileStorage files) {
        this.access = access;
        this.store = store;
        this.files = files;
    }

    @Transactional
    public ObjectDeletion request(UUID actor, UUID objectId) {
        access.lock(actor);
        var pending = store.pending(objectId);
        if (pending.isPresent()) return pending.get();
        store.lockObject(objectId);
        return store.pending(objectId).orElseGet(() -> store.create(objectId, actor, files.files(objectId)));
    }

    @Transactional(readOnly = true)
    public ObjectDeletion get(UUID actor, UUID operationId) {
        access.require(actor);
        return store.get(operationId);
    }
}
