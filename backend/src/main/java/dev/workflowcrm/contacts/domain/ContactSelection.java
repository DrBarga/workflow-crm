package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ContactSelection {
    private final ContactAccess access;
    private final ContactStore contacts;
    private final ContactObjectStore objects;

    ContactSelection(ContactAccess access, ContactStore contacts, ContactObjectStore objects) {
        this.access = access;
        this.contacts = contacts;
        this.objects = objects;
    }

    Contact require(UUID actorId, UUID id) {
        var actor = access.require(actorId);
        access.requireWriter(actor);
        try {
            var contact = contacts.find(id, true);
            if (!actor.sees(contact, objects.linked(id))) throw new ContactNotFoundException();
            return contact;
        } catch (ContactNotFoundException exception) {
            throw new UnprocessableInputException("contactId", "Contact does not exist or is unavailable");
        }
    }
}
