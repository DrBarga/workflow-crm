package dev.workflowcrm.tasks.api;

import java.util.UUID;

/** Published synchronously within the transaction that changes the task status. */
public record TaskStatusChanged(UUID objectId, UUID actorId, State previous, State current) {
    public record State(UUID taskId, String name, String status, long version) {}
}
