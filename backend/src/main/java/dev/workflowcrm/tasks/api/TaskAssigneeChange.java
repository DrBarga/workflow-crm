package dev.workflowcrm.tasks.api;

import java.util.List;
import java.util.UUID;

public interface TaskAssigneeChange {
    record Scope(UUID objectId, UUID managerId, UUID regionId) {}

    record Result(UUID taskId, UUID assigneeId, long version, boolean changed) {}

    List<TaskAssignment.Candidate> options(Scope scope, UUID taskId);

    Result change(Scope scope, UUID taskId, UUID assigneeId, long version, UUID actorId);
}
