package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public interface ContactStore {
    Contact find(UUID id, boolean lock);

    Contact save(Contact contact);

    boolean activeType(UUID id);
}
