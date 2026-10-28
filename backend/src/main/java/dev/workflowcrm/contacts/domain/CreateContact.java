package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.contacts.api.ContactDetails;
import java.util.List;
import java.util.UUID;

public record CreateContact(ContactDetails details, UUID contactTypeId, String comment, List<UUID> objectIds) {
    public CreateContact {
        objectIds = List.copyOf(objectIds);
    }
}
