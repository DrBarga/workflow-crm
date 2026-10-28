package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.audit.api.ContactAudit;
import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.contacts.api.ContactRegistration;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactRegistrationService implements ContactRegistration {
    private final ContactRegistrationStore store;
    private final ObjectAssignmentQuery users;
    private final ContactAudit audit;
    private final ContactSelection selection;

    ContactRegistrationService(
            ContactRegistrationStore store,
            ObjectAssignmentQuery users,
            ContactAudit audit,
            ContactSelection selection) {
        this.store = store;
        this.users = users;
        this.audit = audit;
        this.selection = selection;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID attachImported(UUID objectId, UUID actorId, ContactDetails contact) {
        var actor = users.member(actorId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!actor.active() || !"SUPERADMIN".equals(actor.role())) throw new AccessDeniedException("Access denied");
        UUID id = store.save(new Contact(contact, actorId));
        store.link(objectId, id);
        recordCreation(id, actorId, objectId);
        return id;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID attach(UUID objectId, UUID actorId, UUID existingContactId, ContactDetails newContact) {
        var actor = users.member(actorId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        boolean regional = "REGIONAL_MANAGER".equals(actor.role());
        boolean network = "DEVELOPMENT_DIRECTOR".equals(actor.role());
        if (!actor.active()
                || !Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR")
                        .contains(actor.role())) throw new AccessDeniedException("Access denied");
        boolean editable = network
                ? store.canEditObject(objectId, actorId, null, true, true)
                : actor.regionIds().stream()
                        .anyMatch(region -> store.canEditObject(objectId, actorId, region, regional, false));
        if (!editable) throw new AccessDeniedException("Access denied");
        if ((existingContactId == null) == (newContact == null))
            throw new UnprocessableInputException("contactId", "Choose an existing contact or provide a new contact");
        UUID contactId;
        if (existingContactId != null) {
            selection.require(actorId, existingContactId);
            contactId = existingContactId;
        } else {
            contactId = store.save(new Contact(newContact, actorId));
        }
        store.link(objectId, contactId);
        if (existingContactId == null) recordCreation(contactId, actorId, objectId);
        return contactId;
    }

    private void recordCreation(UUID contact, UUID actor, UUID object) {
        audit.record(
                contact,
                actor,
                0,
                ContactAudit.Action.CREATED,
                List.of("name", "role", "phone", "email", "objectIds"),
                List.of(object));
    }
}
