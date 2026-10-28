package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.TaskStatusChanged;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskCancellation {
    private final JdbcTemplate jdbc;
    private final NotificationQueue notifications;
    private final EventOutbox events;
    private final ApplicationEventPublisher publisher;

    JdbcTaskCancellation(
            JdbcTemplate jdbc,
            NotificationQueue notifications,
            EventOutbox events,
            ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.events = events;
        this.publisher = publisher;
    }

    void cancel(List<TaskTransitionPlan.ExistingTask> tasks, UUID actor) {
        var changes = new java.util.ArrayList<TaskStatusChanged>();
        record Cancellation(UUID taskId, UUID objectId, UUID recipientId, UUID actorId) {}
        for (var task : tasks) {
            var detail = jdbc.queryForObject(
                    "select object_id,assignee_id from tasks where id=?",
                    (r, n) -> new Cancellation(
                            task.id(),
                            r.getObject("object_id", UUID.class),
                            r.getObject("assignee_id", UUID.class),
                            actor),
                    task.id());
            jdbc.update(
                    """
                    insert into task_history(task_id,actor_id,previous_status,new_status,actor_type)
                    select id,?,status,'CANCELLED','SYSTEM' from tasks where id=? and version=?
                    """,
                    actor,
                    task.id(),
                    task.version());
            int changed = jdbc.update(
                    """
                    update tasks set status='CANCELLED',manually_paused=false,version=version+1,updated_at=clock_timestamp()
                    where id=? and version=? and status not in ('DONE','CANCELLED')
                    """,
                    task.id(),
                    task.version());
            if (changed != 1) throw new IllegalStateException("Task changed while applying transition");
            changes.add(new TaskStatusChanged(
                    detail.objectId(),
                    actor,
                    new TaskStatusChanged.State(task.id(), task.name(), task.status(), task.version()),
                    new TaskStatusChanged.State(task.id(), task.name(), "CANCELLED", task.version() + 1)));
            String key = "task-cancelled:" + task.id() + ":" + task.version();
            events.append(key, "TASK_CANCELLED", detail.objectId(), detail);
            notifications.enqueue(detail.recipientId(), key, "TASK_CANCELLED", detail.objectId(), task.id());
        }
        for (var task : tasks) {
            jdbc.update(
                    """
                    update task_sets s set state='CANCELLED',version=version+1,updated_at=clock_timestamp()
                    where s.id=(select task_set_id from tasks where id=?) and s.state='ACTIVE'
                    and not exists(select 1 from tasks t where t.task_set_id=s.id and t.status not in ('DONE','CANCELLED'))
                    """,
                    task.id());
        }
        changes.forEach(publisher::publishEvent);
    }
}
