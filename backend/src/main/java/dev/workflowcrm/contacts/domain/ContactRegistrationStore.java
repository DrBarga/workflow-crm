package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public interface ContactRegistrationStore {
    boolean canEditObject(UUID objectId, UUID actorId, UUID regionId, boolean regionalManager, boolean network);

    UUID save(Contact contact);

    void link(UUID objectId, UUID contactId);
}
