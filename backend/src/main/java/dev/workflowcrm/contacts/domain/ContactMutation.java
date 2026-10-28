package dev.workflowcrm.contacts.domain;

import java.util.List;

public record ContactMutation(ContactCard contact, List<ContactDuplicate> duplicateContacts, String warningCode) {
    public ContactMutation(ContactCard contact, List<ContactDuplicate> duplicateContacts) {
        this(contact, duplicateContacts, duplicateContacts.isEmpty() ? null : "CONTACT_PHONE_DUPLICATE");
    }

    public ContactMutation {
        duplicateContacts = List.copyOf(duplicateContacts);
    }
}
