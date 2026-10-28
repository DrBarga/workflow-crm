package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.*;
import dev.workflowcrm.users.api.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class TaskExecutionService implements TaskExecution {
    private final TaskObjectContext objects;
    private final ObjectAssignmentLock users;
    private final TaskRuntimeStore store;
    private final ApplicationEventPublisher publisher;
    private final EventOutbox events;
    private final NotificationQueue notifications;

    TaskExecutionService(
            TaskObjectContext objects,
            ObjectAssignmentLock users,
            TaskRuntimeStore store,
            ApplicationEventPublisher publisher,
            EventOutbox events,
            NotificationQueue notifications) {
        this.objects = objects;
        this.users = users;
        this.store = store;
        this.publisher = publisher;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional
    public Result complete(UUID objectId, UUID taskId, long version, UUID actorId) {
        var object = objects.lock(objectId);
        var user = users.members(Set.of(actorId)).get(actorId);
        if (user == null
                || !user.active()
                || !Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER")
                        .contains(user.role())
                || (Set.of("MANAGER", "REGIONAL_MANAGER").contains(user.role()) && !user.belongsTo(object.regionId())))
            throw new AccessDeniedException("Access denied");
        var graph = store.lock(objectId);
        var task = graph.require(taskId);
        if (!actorId.equals(task.assigneeId()))
            throw new AccessDeniedException("Only the assignee may complete this task");
        if (task.version() != version)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task changed; reload before completing");
        if ("DONE".equals(task.status())) return new Result(task.id(), task.status(), task.version(), false);
        var blockers = graph.blockers(taskId);
        if ("NEW".equals(task.status()) || !blockers.isEmpty())
            throw new TaskDependenciesBlockedException(
                    blockers.stream().map(TaskRuntimeStore.Node::state).toList());
        if (object.paused()
                || object.terminal()
                || !task.activeSet()
                || !"IN_PROGRESS".equals(task.status())
                || task.manuallyPaused())
            throw new UnprocessableInputException("taskId", "Task is not available for completion");
        if (task.needsReview() && task.resultRecipientId() == null)
            throw new UnprocessableInputException("resultRecipientId", "Task review recipient is not configured");
        String next = task.needsReview() ? "WAITING_APPROVAL" : "DONE";
        var event = store.change(objectId, task, next, actorId, "USER");
        String key = "task-completed:" + taskId + ":" + event.current().version();
        String type = task.needsReview() ? "TASK_REVIEW_REQUESTED" : "TASK_COMPLETED";
        events.append(key, type, objectId, event);
        if (task.resultRecipientId() != null)
            notifications.enqueue(task.resultRecipientId(), key, type, objectId, taskId);
        publisher.publishEvent(event);
        return new Result(taskId, next, event.current().version(), true);
    }
}
