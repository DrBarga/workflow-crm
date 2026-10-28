package dev.workflowcrm.tasks.api;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface ObjectTaskTransition {
    record Context(
            UUID objectId,
            UUID managerId,
            UUID regionId,
            UUID fromStatusId,
            UUID toStatusId,
            String contractType,
            LocalDate startDate,
            boolean generate,
            String legacyContractType,
            boolean allObjectTasks,
            boolean activeSourceSetOnly,
            Map<UUID, UUID> assignees,
            UUID confirmingUserId) {
        public Context(
                UUID objectId,
                UUID managerId,
                UUID regionId,
                UUID fromStatusId,
                UUID toStatusId,
                String contractType,
                LocalDate startDate,
                boolean generate,
                String legacyContractType,
                boolean allObjectTasks,
                boolean activeSourceSetOnly,
                Map<UUID, UUID> assignees) {
            this(
                    objectId,
                    managerId,
                    regionId,
                    fromStatusId,
                    toStatusId,
                    contractType,
                    startDate,
                    generate,
                    legacyContractType,
                    allObjectTasks,
                    activeSourceSetOnly,
                    assignees,
                    null);
        }

        public Context {
            assignees = assignees == null ? Map.of() : Map.copyOf(assignees);
        }

        public Context(
                UUID objectId,
                UUID managerId,
                UUID regionId,
                UUID fromStatusId,
                UUID toStatusId,
                String contractType,
                LocalDate startDate,
                boolean generate,
                String legacyContractType,
                boolean allObjectTasks,
                boolean activeSourceSetOnly) {
            this(
                    objectId,
                    managerId,
                    regionId,
                    fromStatusId,
                    toStatusId,
                    contractType,
                    startDate,
                    generate,
                    legacyContractType,
                    allObjectTasks,
                    activeSourceSetOnly,
                    Map.of());
        }

        public Context(
                UUID objectId,
                UUID managerId,
                UUID regionId,
                UUID fromStatusId,
                UUID toStatusId,
                String contractType,
                LocalDate startDate,
                boolean generate,
                String legacyContractType,
                boolean allObjectTasks) {
            this(
                    objectId,
                    managerId,
                    regionId,
                    fromStatusId,
                    toStatusId,
                    contractType,
                    startDate,
                    generate,
                    legacyContractType,
                    allObjectTasks,
                    false);
        }

        public Context(
                UUID objectId,
                UUID managerId,
                UUID regionId,
                UUID fromStatusId,
                UUID toStatusId,
                String contractType,
                LocalDate startDate,
                boolean generate,
                String legacyContractType) {
            this(
                    objectId,
                    managerId,
                    regionId,
                    fromStatusId,
                    toStatusId,
                    contractType,
                    startDate,
                    generate,
                    legacyContractType,
                    false);
        }

        public Context(
                UUID objectId,
                UUID managerId,
                UUID regionId,
                UUID fromStatusId,
                UUID toStatusId,
                String contractType,
                LocalDate startDate,
                boolean generate) {
            this(
                    objectId,
                    managerId,
                    regionId,
                    fromStatusId,
                    toStatusId,
                    contractType,
                    startDate,
                    generate,
                    null,
                    false);
        }
    }

    TaskTransitionPlan preview(Context context);

    TaskSetPreview previewSet(Context context);

    void apply(Context context, TaskTransitionPlan plan, String unfinishedAction, UUID actorId);

    boolean hasTasks(UUID objectId);

    boolean hasContractTypeTaskSet(UUID objectId);

    java.util.List<TaskTransitionPlan.ExistingTask> unfinished(Context context);
}
