package dev.workflowcrm.objects.domain;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ObjectCatalogService {
    private final ObjectCardAccess access;
    private final ObjectCatalogStore store;

    ObjectCatalogService(ObjectCardAccess access, ObjectCatalogStore store) {
        this.access = access;
        this.store = store;
    }

    public List<ObjectCatalogStore.Option> dataSources(UUID actor) {
        access.require(actor);
        return store.dataSources();
    }

    public List<ObjectCatalogStore.Option> contractTypes(UUID actor) {
        access.require(actor);
        return store.contractTypes();
    }
}
