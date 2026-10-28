package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class TaskSetPlanner {
    private final TaskAssigneeResolver assignees;

    TaskSetPlanner(TaskAssigneeResolver assignees) {
        this.assignees = assignees;
    }

    TaskTransitionPlan plan(ObjectTaskTransition.Context context, TaskTransitionPlan template) {
        var items = template.tasks().stream()
                .map(TaskTransitionPlan.Task::templateItemId)
                .collect(java.util.stream.Collectors.toSet());
        if (!items.containsAll(context.assignees().keySet()))
            throw new UnprocessableInputException(
                    "assignees", "Selection refers to an item outside the current template");
        var lookup = assignees.lookup(context.managerId(), context.regionId(), context.confirmingUserId());
        var resolved = template.tasks().stream()
                .map(task -> {
                    var rule = task.assignment();
                    var selection = lookup.selected(
                            lookup.resolve(rule, task.assigneeId()),
                            context.assignees().get(task.templateItemId()));
                    var assignee = selection.member();
                    var recipient = task.resultRecipientRoleId() == null
                            ? null
                            : lookup.role(task.resultRecipientRoleId(), false).member();
                    return new TaskTransitionPlan.Task(
                            task.templateItemId(),
                            task.name(),
                            assignee == null ? null : assignee.id(),
                            task.deadline(),
                            assignee == null ? null : assignee.fullName(),
                            task.needsReview(),
                            task.resultRecipientRoleId(),
                            task.escalationDays(),
                            task.predecessors(),
                            lookup.description(rule, selection),
                            recipient == null ? null : recipient.id(),
                            task.code());
                })
                .toList();
        validateDependencies(resolved);
        return new TaskTransitionPlan(template.templateId(), template.revision(), resolved, template.unfinished());
    }

    private static void validateDependencies(List<TaskTransitionPlan.Task> tasks) {
        var dependencies = new HashMap<UUID, Set<UUID>>();
        tasks.forEach(task -> dependencies.put(task.templateItemId(), Set.copyOf(task.predecessors())));
        try {
            TemplateDependencies.validate(dependencies);
        } catch (IllegalArgumentException exception) {
            throw new TaskTemplateConfigurationException("Task template dependencies are invalid");
        }
    }
}
