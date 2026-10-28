package dev.workflowcrm.tasks.api;

import java.util.UUID;

public interface StatusTaskTriggerQuery {
    boolean isTrigger(UUID statusId);
}
