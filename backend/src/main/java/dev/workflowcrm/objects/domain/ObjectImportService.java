package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectImportService {
    private final ObjectImportAccess access;
    private final ObjectImportStore store;

    public ObjectImportService(ObjectImportAccess access, ObjectImportStore store) {
        this.access = access;
        this.store = store;
    }

    @Transactional
    public ObjectImport.View request(UUID actor, String hash, List<ObjectImport.Input> rows) {
        access.require(actor);
        return store.enqueue(actor, hash, rows);
    }

    @Transactional(readOnly = true)
    public ObjectImport.View status(UUID actor, UUID id) {
        access.require(actor);
        return store.view(id);
    }

    @Transactional(readOnly = true)
    public ObjectImport.Report report(UUID actor, UUID id, int offset, int limit) {
        access.require(actor);
        if (offset < 0 || limit < 1 || limit > 200)
            throw new UnprocessableInputException("pagination", "Use offset >= 0 and limit 1..200");
        store.view(id);
        return store.report(id, offset, limit);
    }
}
