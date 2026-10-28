package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskSetPreview;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class StatusTaskTransition implements ObjectTaskTransition {
    private final TaskTransitionStore store;
    private final TaskSetPlanner planner;
    private final org.springframework.context.ApplicationEventPublisher events;

    StatusTaskTransition(
            TaskTransitionStore store,
            TaskSetPlanner planner,
            org.springframework.context.ApplicationEventPublisher events) {
        this.store = store;
        this.planner = planner;
        this.events = events;
    }

    public TaskTransitionPlan preview(Context context) {
        var unfinished = store.unfinished(context);
        if (context.generate() && store.hasActiveSet(context)) {
            requireNoSelections(context);
            return new TaskTransitionPlan(null, "", List.of(), unfinished, true);
        }
        var template = context.generate() ? store.template(context) : java.util.Optional.<TaskTransitionPlan>empty();
        if (template.isEmpty() || template.get().tasks().isEmpty()) {
            requireNoSelections(context);
            return new TaskTransitionPlan(null, "", List.of(), unfinished);
        }
        var plan = planner.plan(context, template.get());
        return new TaskTransitionPlan(plan.templateId(), plan.revision(), plan.tasks(), unfinished);
    }

    public TaskSetPreview previewSet(Context context) {
        var template = store.template(context).filter(plan -> !plan.tasks().isEmpty());
        if (template.isEmpty()) requireNoSelections(context);
        var plan = template.map(value -> planner.plan(context, value)).orElse(null);
        return new TaskSetPreview(
                plan == null ? null : plan.templateId(),
                plan == null ? "" : plan.revision(),
                plan != null,
                context.startDate(),
                plan == null ? List.of() : plan.tasks());
    }

    public void apply(Context context, TaskTransitionPlan plan, String action, UUID actor) {
        if (plan.tasks().stream().anyMatch(task -> task.assignment().selectionRequired()))
            throw invalid("Choose an active assignee for every task before confirming");
        switch (action) {
            case "CANCEL", "CANCEL_ALL" -> store.cancel(plan.unfinished(), actor);
            case "BLOCK" -> {
                if (!plan.unfinished().isEmpty())
                    throw new dev.workflowcrm.tasks.api.UnfinishedTasksException(plan.unfinished());
            }
            case "NONE" -> {}
            default -> throw new IllegalStateException("Unknown unfinished task action");
        }
        if (plan.templateId() != null)
            events.publishEvent(new dev.workflowcrm.tasks.api.TaskGenerationRequested(context, plan, actor));
    }

    public boolean hasTasks(UUID objectId) {
        return store.hasTasks(objectId);
    }

    public boolean hasContractTypeTaskSet(UUID objectId) {
        return store.hasContractTypeTaskSet(objectId);
    }

    public List<TaskTransitionPlan.ExistingTask> unfinished(Context context) {
        return store.unfinished(context);
    }

    private static UnprocessableInputException invalid(String message) {
        return new UnprocessableInputException("tasks", message);
    }

    private static void requireNoSelections(Context context) {
        if (!context.assignees().isEmpty())
            throw new UnprocessableInputException("assignees", "There are no template items to assign");
    }
}
