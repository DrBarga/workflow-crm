package dev.workflowcrm.contacts.domain;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ContactCards {
    private final ContactAccess access;

    ContactCards(ContactAccess access) {
        this.access = access;
    }

    ContactCard of(Contact contact, ContactScope actor, List<ContactObject> objects) {
        if (!actor.sees(contact, objects)) throw new ContactNotFoundException();
        var details = contact.details();
        boolean editable = access.canEdit(actor, contact, objects);
        return new ContactCard(
                contact.getId(),
                details.name(),
                details.role(),
                details.phone() == null ? null : "***",
                details.email(),
                contact.getComment(),
                contact.getContactTypeId(),
                contact.getAuthorId(),
                contact.getCreatedAt(),
                contact.getUpdatedAt(),
                contact.getVersion(),
                objects.stream()
                        .filter(actor::sees)
                        .map(o -> new ContactCard.LinkedObject(o.id(), o.name(), o.status()))
                        .toList(),
                editable,
                access.canDelete(actor));
    }
}
