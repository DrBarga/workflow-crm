package dev.workflowcrm.tasks.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TransitionTaskRequirements {
    Set<String> missing(UUID objectId, UUID statusId, Set<String> requiredCodes);

    Assessment requirements(UUID objectId, UUID statusId, Set<String> requiredCodes);

    Assessment inspect(UUID objectId, UUID statusId, Set<String> requiredCodes);

    record Task(UUID id, String code, String name, String status) {}

    record Assessment(Set<String> missingCodes, List<Task> incompleteTasks) {}
}
