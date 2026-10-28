package dev.workflowcrm.tasks.domain;

public enum TaskStatus {
    NEW,
    IN_PROGRESS,
    PAUSED,
    WAITING_APPROVAL,
    DONE,
    CANCELLED;

    public boolean terminal() {
        return this == DONE || this == CANCELLED;
    }
}
