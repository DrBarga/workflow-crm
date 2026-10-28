package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.tasks.api.TaskAssignment;
import java.util.UUID;

public interface TaskAssignmentStore {
    record Task(
            UUID id,
            UUID assigneeId,
            String status,
            long version,
            TaskAssignment assignment,
            boolean transferredFromPredecessor) {}

    Task lock(UUID objectId, UUID taskId);

    void save(UUID objectId, Task task, UUID assigneeId, UUID actorId);
}
