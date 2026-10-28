package dev.workflowcrm.tasks.api;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.List;

public class UnfinishedTasksException extends UnprocessableInputException {
    private final List<TaskTransitionPlan.ExistingTask> tasks;

    public UnfinishedTasksException(List<TaskTransitionPlan.ExistingTask> tasks) {
        super("tasks", "Complete unfinished tasks before transitioning");
        this.tasks = List.copyOf(tasks);
    }

    public List<TaskTransitionPlan.ExistingTask> tasks() {
        return tasks;
    }
}
