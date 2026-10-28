package dev.workflowcrm.contacts.api;

import java.util.UUID;

public interface ObjectContactSelection {
    ContactDetails replace(UUID actorId, UUID objectId, UUID contactId);
}
