package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.TaskAssignment;
import dev.workflowcrm.tasks.domain.TaskAssignmentStore;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcTaskAssignmentStore implements TaskAssignmentStore {
    private final JdbcTemplate jdbc;
    private final EventOutbox events;

    JdbcTaskAssignmentStore(JdbcTemplate jdbc, EventOutbox events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public Task lock(UUID objectId, UUID taskId) {
        return jdbc
                .query(
                        """
                select t.id,t.assignee_id,t.status,t.version,t.transferred_from_predecessor,
                    coalesce(i.assignment_rule,d.assignment_rule,'EXPLICIT_USER') assignment_rule,
                    coalesce(i.role_id,d.role_id) role_id
                from tasks t left join template_items i on i.id=t.source_item_id
                left join task_template_items d on d.id=t.template_item_id and t.source_item_id is null
                where t.object_id=? and t.id=? for update of t
                """,
                        (r, n) -> new Task(
                                r.getObject("id", UUID.class),
                                r.getObject("assignee_id", UUID.class),
                                r.getString("status"),
                                r.getLong("version"),
                                new TaskAssignment(
                                        r.getString("assignment_rule"), r.getObject("role_id", UUID.class), null),
                                r.getBoolean("transferred_from_predecessor")),
                        objectId,
                        taskId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public void save(UUID objectId, Task task, UUID assigneeId, UUID actorId) {
        int changed = jdbc.update(
                """
                update tasks set assignee_id=?,version=version+1,updated_at=clock_timestamp()
                where id=? and object_id=? and version=? and assignee_id=? and status not in ('DONE','CANCELLED')
                """,
                assigneeId,
                task.id(),
                objectId,
                task.version(),
                task.assigneeId());
        if (changed != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task changed; reload before assigning");
        jdbc.update(
                """
                insert into task_assignment_history(task_id,actor_id,previous_assignee_id,new_assignee_id,task_version)
                values (?,?,?,?,?)
                """,
                task.id(),
                actorId,
                task.assigneeId(),
                assigneeId,
                task.version() + 1);
        var transfer = new dev.workflowcrm.tasks.api.ObjectTaskReassignment.Transfer(
                task.id(),
                task.assigneeId(),
                assigneeId,
                task.status(),
                task.version() + 1,
                task.transferredFromPredecessor(),
                null);
        record Detail(UUID objectId, UUID actorId, dev.workflowcrm.tasks.api.ObjectTaskReassignment.Transfer task) {}
        events.append(
                "task-reassigned:" + task.id() + ":" + (task.version() + 1),
                "TASK_REASSIGNED",
                objectId,
                new Detail(objectId, actorId, transfer));
    }
}
