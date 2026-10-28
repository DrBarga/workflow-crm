package dev.workflowcrm.contacts.domain;

import java.util.List;
import java.util.UUID;

public interface ContactDuplicateStore {
    List<ContactDuplicate> find(List<String> phoneKeys, UUID excludedId, ContactScope scope);
}
