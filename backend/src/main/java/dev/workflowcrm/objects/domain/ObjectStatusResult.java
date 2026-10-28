package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.List;
import java.util.UUID;

public record ObjectStatusResult(
        boolean saved,
        UUID id,
        String status,
        long version,
        UUID approvalRequestId,
        String confirmationToken,
        List<TaskTransitionPlan.Task> tasks,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                ObjectTransitionConditions unmetConditions) {
    public ObjectStatusResult(
            boolean saved,
            UUID id,
            String status,
            long version,
            UUID approvalRequestId,
            String confirmationToken,
            List<TaskTransitionPlan.Task> tasks) {
        this(saved, id, status, version, approvalRequestId, confirmationToken, tasks, null);
    }

    ObjectStatusResult withConditions(ObjectTransitionConditions conditions) {
        return new ObjectStatusResult(
                saved, id, status, version, approvalRequestId, confirmationToken, tasks, conditions);
    }
}
