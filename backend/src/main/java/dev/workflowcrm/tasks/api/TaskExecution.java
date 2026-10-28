package dev.workflowcrm.tasks.api;

import java.util.UUID;

public interface TaskExecution {
    record Result(UUID taskId, String status, long version, boolean changed) {}

    Result complete(UUID objectId, UUID taskId, long version, UUID actorId);
}
