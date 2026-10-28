package dev.workflowcrm.contacts.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactObjectStore {
    Optional<ContactObject> find(UUID objectId);

    List<ContactObject> linked(UUID contactId);

    List<ContactObject> objects(List<UUID> objectIds);

    void link(UUID contactId, List<UUID> objectIds);
}
