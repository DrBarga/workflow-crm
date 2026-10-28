package dev.workflowcrm.tasks.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaskSetPreview(
        UUID templateId,
        String revision,
        boolean templateFound,
        LocalDate triggerDate,
        List<TaskTransitionPlan.Task> tasks) {
    public TaskSetPreview {
        tasks = List.copyOf(tasks);
    }
}
