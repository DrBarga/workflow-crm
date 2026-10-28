package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.audit.api.ContactAudit;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {
    private final ContactAccess access;
    private final ContactStore store;
    private final ContactObjectStore objects;
    private final ContactDuplicateStore duplicates;
    private final ContactPhone phones;
    private final ContactCards cards;
    private final ContactAudit audit;

    ContactService(
            ContactAccess access,
            ContactStore store,
            ContactObjectStore objects,
            ContactDuplicateStore duplicates,
            ContactPhone phones,
            ContactCards cards,
            ContactAudit audit) {
        this.access = access;
        this.store = store;
        this.objects = objects;
        this.duplicates = duplicates;
        this.phones = phones;
        this.cards = cards;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ContactCard get(UUID actorId, UUID id) {
        var actor = access.require(actorId);
        var contact = store.find(id, false);
        return cards.of(contact, actor, objects.linked(id));
    }

    @Transactional
    public ContactMutation create(UUID actorId, CreateContact command) {
        var actor = access.require(actorId);
        access.requireWriter(actor);
        var links = objects.objects(command.objectIds());
        if (links.size() != command.objectIds().size() || links.stream().anyMatch(o -> !actor.sees(o)))
            throw new UnprocessableInputException("objectIds", "Objects do not exist or are unavailable");
        type(command.contactTypeId());
        var contact = new Contact(command.details(), actorId);
        contact.describe(command.contactTypeId(), command.comment());
        store.save(contact);
        objects.link(contact.getId(), command.objectIds());
        audit.record(
                contact.getId(),
                actorId,
                contact.getVersion(),
                ContactAudit.Action.CREATED,
                List.of("name", "role", "phone", "email", "comment", "contactTypeId", "objectIds"),
                command.objectIds());
        return mutation(contact, actor, links);
    }

    @Transactional
    public ContactMutation update(UUID actorId, UUID id, ContactPatch patch) {
        var actor = access.require(actorId);
        access.requireWriter(actor);
        var contact = store.find(id, true);
        var links = objects.linked(id);
        access.edit(actor, contact, links);
        contact.requireVersion(patch.version());
        if (patch.supplied().contains("contactTypeId")) type(patch.typeId());
        var changed = patch.apply(contact);
        if (!changed.isEmpty()) {
            store.save(contact);
            audit.record(
                    id,
                    actorId,
                    contact.getVersion(),
                    ContactAudit.Action.UPDATED,
                    changed.keySet().stream().sorted().toList(),
                    links.stream().map(ContactObject::id).toList(),
                    changed);
        }
        return mutation(contact, actor, links);
    }

    private ContactMutation mutation(Contact contact, ContactScope actor, List<ContactObject> links) {
        return new ContactMutation(
                cards.of(contact, actor, links),
                duplicates.find(phones.searchKeys(contact.details().phone()), contact.getId(), actor));
    }

    private void type(UUID id) {
        if (id != null && !store.activeType(id))
            throw new UnprocessableInputException("contactTypeId", "Contact type does not exist or is inactive");
    }
}
