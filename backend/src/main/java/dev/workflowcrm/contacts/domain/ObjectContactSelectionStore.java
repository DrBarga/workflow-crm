package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public interface ObjectContactSelectionStore {
    void replace(UUID objectId, UUID contactId);
}
