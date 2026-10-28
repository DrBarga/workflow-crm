package dev.workflowcrm.tasks.api;

import java.util.List;

public class TaskDependenciesBlockedException extends RuntimeException {
    private final List<TaskStatusChanged.State> blockingTasks;

    public TaskDependenciesBlockedException(List<TaskStatusChanged.State> blockingTasks) {
        super("Complete every predecessor before executing this task");
        this.blockingTasks = List.copyOf(blockingTasks);
    }

    public List<TaskStatusChanged.State> blockingTasks() {
        return blockingTasks;
    }
}
