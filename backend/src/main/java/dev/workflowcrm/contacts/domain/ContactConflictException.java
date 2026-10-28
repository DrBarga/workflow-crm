package dev.workflowcrm.contacts.domain;

public final class ContactConflictException extends RuntimeException {
    private final long currentVersion;
    private final String confirmationToken;

    public ContactConflictException(long currentVersion, String confirmationToken) {
        super(
                confirmationToken == null
                        ? "Contact has changed; reload before editing"
                        : "Confirm deletion of a contact linked to active objects");
        this.currentVersion = currentVersion;
        this.confirmationToken = confirmationToken;
    }

    public long currentVersion() {
        return currentVersion;
    }

    public String confirmationToken() {
        return confirmationToken;
    }
}
