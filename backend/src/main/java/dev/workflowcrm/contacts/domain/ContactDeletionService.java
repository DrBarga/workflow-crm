package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.audit.api.ContactAudit;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactDeletionService {
    private final ContactAccess access;
    private final ContactStore store;
    private final ContactObjectStore objects;
    private final ContactAudit audit;

    ContactDeletionService(ContactAccess access, ContactStore store, ContactObjectStore objects, ContactAudit audit) {
        this.access = access;
        this.store = store;
        this.objects = objects;
        this.audit = audit;
    }

    @Transactional
    public void delete(UUID actorId, UUID id, long expectedVersion, String confirmationToken) {
        var actor = access.require(actorId);
        access.requireDeletion(actor);
        var contact = store.find(id, true);
        var links = objects.linked(id);
        contact.requireVersion(expectedVersion);
        if (links.stream().anyMatch(o -> !o.finalStatus())) {
            String current = ContactDeletionConfirmation.token(actorId, contact, links);
            if (!current.equals(confirmationToken)) throw new ContactConflictException(contact.getVersion(), current);
        }
        contact.delete(Instant.now());
        store.save(contact);
        audit.record(
                id,
                actorId,
                contact.getVersion(),
                ContactAudit.Action.DELETED,
                List.of("deletedAt"),
                links.stream().map(ContactObject::id).toList());
    }
}
