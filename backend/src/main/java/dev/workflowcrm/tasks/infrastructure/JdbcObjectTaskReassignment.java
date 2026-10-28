package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.ObjectTaskReassignment;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class JdbcObjectTaskReassignment implements ObjectTaskReassignment {
    private final JdbcTemplate jdbc;
    private final EventOutbox events;

    JdbcObjectTaskReassignment(JdbcTemplate jdbc, EventOutbox events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public List<Transfer> transfer(UUID object, UUID previous, UUID manager, UUID actor) {
        if (previous.equals(manager)) return List.of();
        record Task(UUID id, String status, long version) {}
        var tasks = jdbc.query(
                """
                select id,status,version from tasks where object_id=? and assignee_id=?
                and status not in ('DONE','CANCELLED') order by id for update
                """,
                (r, n) -> new Task(r.getObject("id", UUID.class), r.getString("status"), r.getLong("version")),
                object,
                previous);
        return tasks.stream()
                .map(task -> {
                    int changed = jdbc.update(
                            """
                    update tasks set assignee_id=?,transferred_from_predecessor=true,
                    version=version+1,updated_at=clock_timestamp()
                    where id=? and version=? and assignee_id=? and status not in ('DONE','CANCELLED')
                    """,
                            manager,
                            task.id(),
                            task.version(),
                            previous);
                    if (changed != 1) throw new IllegalStateException("Task changed while reassigning object");
                    jdbc.update(
                            """
                    insert into task_assignment_history(task_id,actor_id,previous_assignee_id,
                        new_assignee_id,task_version) values (?,?,?,?,?)
                    """,
                            task.id(),
                            actor,
                            previous,
                            manager,
                            task.version() + 1);
                    var transfer = new Transfer(
                            task.id(),
                            previous,
                            manager,
                            task.status(),
                            task.version() + 1,
                            true,
                            "задача перейшла від попередника");
                    record Detail(UUID objectId, UUID actorId, Transfer task) {}
                    events.append(
                            "task-reassigned:" + task.id() + ":" + transfer.version(),
                            "TASK_REASSIGNED",
                            object,
                            new Detail(object, actor, transfer));
                    return transfer;
                })
                .toList();
    }
}
