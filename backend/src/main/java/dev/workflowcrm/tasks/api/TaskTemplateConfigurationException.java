package dev.workflowcrm.tasks.api;

public class TaskTemplateConfigurationException extends RuntimeException {
    public TaskTemplateConfigurationException() {
        super("Multiple active task templates match the trigger status and contract type");
    }

    public TaskTemplateConfigurationException(String message) {
        super(message);
    }
}
