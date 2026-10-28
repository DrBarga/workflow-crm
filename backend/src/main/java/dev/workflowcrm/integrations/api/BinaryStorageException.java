package dev.workflowcrm.integrations.api;

public class BinaryStorageException extends RuntimeException {
    public BinaryStorageException(Throwable cause) {
        super("File storage operation failed", cause);
    }
}
