package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class TaskDependencyService implements TaskDependencies {
    private final TaskObjectContext objects;
    private final TaskRuntimeStore store;
    private final ApplicationEventPublisher publisher;
    private final NotificationQueue notifications;
    private final EventOutbox events;

    TaskDependencyService(
            TaskObjectContext objects,
            TaskRuntimeStore store,
            ApplicationEventPublisher publisher,
            NotificationQueue notifications,
            EventOutbox events) {
        this.objects = objects;
        this.store = store;
        this.publisher = publisher;
        this.notifications = notifications;
        this.events = events;
    }

    @EventListener
    public void changed(TaskStatusChanged event) {
        if (Set.of("DONE", "CANCELLED").contains(event.current().status()))
            synchronize(event.objectId(), event.actorId());
    }

    public void synchronize(UUID objectId, UUID actorId) {
        var object = objects.lock(objectId);
        var graph = store.lock(objectId);
        var plan = TaskDependencyGraph.plan(graph, !object.paused() && !object.terminal());
        var changes = new ArrayList<TaskStatusChanged>();
        for (var change : plan.changes()) {
            var event = store.change(objectId, change.task(), change.status(), actorId, "SYSTEM");
            changes.add(event);
            boolean started = "IN_PROGRESS".equals(change.status());
            String type = started ? "TASK_STARTED" : "TASK_CANCELLED";
            String key = started
                    ? "task-started:" + change.task().id() + ":"
                            + event.current().version()
                    : "task-cancelled:" + change.task().id() + ":"
                            + change.task().version();
            events.append(key, type, objectId, event);
            notifications.enqueue(
                    change.task().assigneeId(),
                    key,
                    type,
                    objectId,
                    change.task().id());
        }
        plan.sets().forEach((id, state) -> store.setState(objectId, id, state));
        changes.forEach(publisher::publishEvent);
    }
}
