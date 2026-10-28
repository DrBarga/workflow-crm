package dev.workflowcrm.tasks.api;

import java.util.List;
import java.util.UUID;

public interface ObjectTaskReassignment {
    record Transfer(
            UUID taskId,
            UUID previousAssigneeId,
            UUID assigneeId,
            String status,
            long version,
            boolean transferredFromPredecessor,
            String label) {}

    List<Transfer> transfer(UUID object, UUID previousManager, UUID manager, UUID actor);
}
