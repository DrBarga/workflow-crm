package dev.workflowcrm.tasks.api;

import java.util.UUID;

public interface TaskDependencies {
    void synchronize(UUID objectId, UUID actorId);
}
