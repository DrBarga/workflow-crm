package dev.workflowcrm.contacts.api;

import java.util.UUID;

public interface ContactRegistration {
    UUID attach(UUID objectId, UUID actorId, UUID existingContactId, ContactDetails newContact);

    UUID attachImported(UUID objectId, UUID actorId, ContactDetails contact);
}
