package dev.workflowcrm.objects.domain;

public class ObjectDuplicatesFoundException extends RuntimeException {
    private final ObjectDuplicateReport duplicates;

    public ObjectDuplicatesFoundException(ObjectDuplicateReport duplicates) {
        super("Possible duplicates found; confirm creation or cancel");
        this.duplicates = duplicates;
    }

    public ObjectDuplicateReport duplicates() {
        return duplicates;
    }
}
