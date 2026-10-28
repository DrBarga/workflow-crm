package dev.workflowcrm.tasks.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaskTransitionPlan(
        UUID templateId, String revision, List<Task> tasks, List<ExistingTask> unfinished, boolean activeTaskSet) {
    public TaskTransitionPlan(UUID templateId, String revision, List<Task> tasks, List<ExistingTask> unfinished) {
        this(templateId, revision, tasks, unfinished, false);
    }

    public boolean missingTemplate() {
        return templateId == null && !activeTaskSet;
    }

    public TaskTransitionPlan {
        tasks = List.copyOf(tasks);
        unfinished = List.copyOf(unfinished);
    }

    public record Task(
            UUID templateItemId,
            String name,
            UUID assigneeId,
            LocalDate deadline,
            String assigneeName,
            boolean needsReview,
            UUID resultRecipientRoleId,
            Integer escalationDays,
            List<UUID> predecessors,
            TaskAssignment assignment,
            UUID resultRecipientId,
            String code) {
        public Task(
                UUID templateItemId,
                String name,
                UUID assigneeId,
                LocalDate deadline,
                String assigneeName,
                boolean needsReview,
                UUID resultRecipientRoleId,
                Integer escalationDays,
                List<UUID> predecessors,
                TaskAssignment assignment,
                UUID resultRecipientId) {
            this(
                    templateItemId,
                    name,
                    assigneeId,
                    deadline,
                    assigneeName,
                    needsReview,
                    resultRecipientRoleId,
                    escalationDays,
                    predecessors,
                    assignment,
                    resultRecipientId,
                    null);
        }

        public Task {
            predecessors = List.copyOf(predecessors);
        }

        public Task(
                UUID templateItemId,
                String name,
                UUID assigneeId,
                LocalDate deadline,
                String assigneeName,
                boolean needsReview,
                UUID resultRecipientRoleId,
                Integer escalationDays) {
            this(
                    templateItemId,
                    name,
                    assigneeId,
                    deadline,
                    assigneeName,
                    needsReview,
                    resultRecipientRoleId,
                    escalationDays,
                    List.of(),
                    new TaskAssignment("EXPLICIT_USER", null, null),
                    null);
        }

        public Task(UUID templateItemId, String name, UUID assigneeId, LocalDate deadline, String assigneeName) {
            this(templateItemId, name, assigneeId, deadline, assigneeName, true, null, null);
        }

        public Task(UUID templateItemId, String name, UUID assigneeId, LocalDate deadline) {
            this(templateItemId, name, assigneeId, deadline, null);
        }
    }

    public record ExistingTask(UUID id, long version, String name, String status, UUID assigneeId) {
        public ExistingTask(UUID id, long version) {
            this(id, version, null, null, null);
        }
    }
}
