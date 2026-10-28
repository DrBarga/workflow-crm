package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class TaskAssigneeChangeService implements TaskAssigneeChange {
    private final TaskAssignmentStore store;
    private final TaskAssigneeResolver assignees;

    TaskAssigneeChangeService(TaskAssignmentStore store, TaskAssigneeResolver assignees) {
        this.store = store;
        this.assignees = assignees;
    }

    public List<TaskAssignment.Candidate> options(Scope scope, UUID taskId) {
        var task = store.lock(scope.objectId(), taskId);
        requireOpen(task);
        return options(scope, task);
    }

    public Result change(Scope scope, UUID taskId, UUID assigneeId, long version, UUID actorId) {
        var task = store.lock(scope.objectId(), taskId);
        if (task.version() != version)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task changed; reload before assigning");
        requireOpen(task);
        if (options(scope, task).stream().noneMatch(candidate -> candidate.id().equals(assigneeId)))
            throw new UnprocessableInputException(
                    "assigneeId", "Choose an active assignee from the available role and region");
        if (task.assigneeId().equals(assigneeId)) return new Result(taskId, assigneeId, version, false);
        store.save(scope.objectId(), task, assigneeId, actorId);
        return new Result(taskId, assigneeId, version + 1, true);
    }

    private List<TaskAssignment.Candidate> options(Scope scope, TaskAssignmentStore.Task task) {
        return TaskAssigneeResolver.options(
                assignees.lookup(scope.managerId(), scope.regionId(), null).replacementOptions(task.assignment()));
    }

    private static void requireOpen(TaskAssignmentStore.Task task) {
        if (List.of("DONE", "CANCELLED").contains(task.status()))
            throw new UnprocessableInputException("taskId", "Completed or cancelled tasks cannot be reassigned");
    }
}
