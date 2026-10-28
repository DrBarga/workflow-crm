package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.contacts.api.ObjectContactSelection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class ObjectContactSelectionService implements ObjectContactSelection {
    private final ObjectContactSelectionStore store;
    private final ContactSelection selection;

    ObjectContactSelectionService(ObjectContactSelectionStore store, ContactSelection selection) {
        this.store = store;
        this.selection = selection;
    }

    public ContactDetails replace(UUID actorId, UUID objectId, UUID contactId) {
        var contact = selection.require(actorId, contactId);
        store.replace(objectId, contactId);
        return contact.details();
    }
}
