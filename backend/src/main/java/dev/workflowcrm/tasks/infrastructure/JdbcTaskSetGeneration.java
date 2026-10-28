package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTaskSetGeneration {
    private final JdbcTemplate jdbc;
    private final JdbcTaskTemplateSnapshot snapshots;
    private final NotificationQueue notifications;

    JdbcTaskSetGeneration(JdbcTemplate jdbc, JdbcTaskTemplateSnapshot snapshots, NotificationQueue notifications) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID generate(ObjectTaskTransition.Context context, TaskTransitionPlan plan, UUID actor) {
        UUID version = snapshots.capture(context, plan);
        UUID set = UUID.randomUUID();
        jdbc.update(
                """
                insert into task_sets(id,object_id,template_version_id,trigger_status_id) values(?,?,?,?)
                """,
                set,
                context.objectId(),
                version,
                context.toStatusId());
        for (var task : plan.tasks()) {
            UUID id = UUID.randomUUID();
            int inserted = jdbc.update(
                    """
                    insert into tasks(id,object_id,source_status_id,template_item_id,name,assignee_id,deadline,
                        author_id,task_set_id,template_version_id,source_item_id,author_type,needs_review,result_recipient_id,status)
                    select ?,?,?,?,?,?,?,?,?,?,id,'SYSTEM',needs_review,?,? from template_items
                    where template_version_id=? and source_item_id=?
                    """,
                    id,
                    context.objectId(),
                    context.toStatusId(),
                    task.templateItemId(),
                    task.name(),
                    task.assigneeId(),
                    task.deadline(),
                    actor,
                    set,
                    version,
                    task.resultRecipientId(),
                    task.predecessors().isEmpty() ? "IN_PROGRESS" : "NEW",
                    version,
                    task.templateItemId());
            if (inserted != 1) throw new IllegalStateException("Task template item is missing from its version");
            if (task.predecessors().isEmpty())
                notifications.enqueue(task.assigneeId(), "task-started:" + id, "TASK_STARTED", context.objectId(), id);
        }
        jdbc.update(
                """
                insert into task_dependencies(task_id,predecessor_id,object_id)
                select task.id,predecessor.id,? from tasks task
                join template_item_dependencies dependency on dependency.item_id=task.source_item_id
                join tasks predecessor on predecessor.source_item_id=dependency.predecessor_id
                    and predecessor.task_set_id=task.task_set_id
                where task.task_set_id=?
                """,
                context.objectId(),
                set);
        return set;
    }
}
