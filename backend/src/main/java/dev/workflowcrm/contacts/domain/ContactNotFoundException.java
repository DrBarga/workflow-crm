package dev.workflowcrm.contacts.domain;

public final class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException() {
        super("Contact not found");
    }
}
