package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.ObjectTaskReassignment;
import java.util.List;
import java.util.UUID;

public record ObjectReassignmentResult(
        boolean saved,
        UUID id,
        UUID previousManagerId,
        UUID managerId,
        long version,
        List<ObjectTaskReassignment.Transfer> transferredTasks) {}
