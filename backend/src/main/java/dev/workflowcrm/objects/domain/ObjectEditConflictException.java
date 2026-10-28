package dev.workflowcrm.objects.domain;

public class ObjectEditConflictException extends RuntimeException {
    private final long currentVersion;

    public ObjectEditConflictException(long currentVersion) {
        super("Object has changed; reload before editing");
        this.currentVersion = currentVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
