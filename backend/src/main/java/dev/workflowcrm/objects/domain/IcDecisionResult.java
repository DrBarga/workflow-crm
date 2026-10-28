package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IcDecisionResult(
        boolean saved,
        UUID id,
        String status,
        long version,
        InvestmentCommitteeDecision decision,
        LocalDate date,
        String conditions,
        String refusalComment,
        String confirmationToken,
        List<TaskTransitionPlan.Task> tasks) {}
