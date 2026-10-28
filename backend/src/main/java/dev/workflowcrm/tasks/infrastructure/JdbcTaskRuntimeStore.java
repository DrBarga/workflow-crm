package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.TaskStatusChanged;
import dev.workflowcrm.tasks.domain.TaskRuntimeStore;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcTaskRuntimeStore implements TaskRuntimeStore {
    private final JdbcTemplate jdbc;

    JdbcTaskRuntimeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Graph lock(UUID objectId) {
        var tasks = jdbc.query(
                """
                select t.id,t.task_set_id,s.state,t.name,t.assignee_id,t.status,t.version,
                    t.manually_paused,t.needs_review,t.result_recipient_id
                from tasks t left join task_sets s on s.id=t.task_set_id and s.object_id=t.object_id
                where t.object_id=? order by t.id for update of t
                """,
                (r, n) -> new Node(
                        r.getObject("id", UUID.class),
                        r.getObject("task_set_id", UUID.class),
                        r.getString("state"),
                        r.getString("name"),
                        r.getObject("assignee_id", UUID.class),
                        r.getString("status"),
                        r.getLong("version"),
                        r.getBoolean("manually_paused"),
                        r.getBoolean("needs_review"),
                        r.getObject("result_recipient_id", UUID.class)),
                objectId);
        var predecessors = new HashMap<UUID, List<UUID>>();
        jdbc.query(
                "select task_id,predecessor_id from task_dependencies where object_id=? order by task_id,predecessor_id",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> predecessors
                        .computeIfAbsent(r.getObject("task_id", UUID.class), key -> new ArrayList<>())
                        .add(r.getObject("predecessor_id", UUID.class)),
                objectId);
        return new Graph(tasks, predecessors);
    }

    public TaskStatusChanged change(UUID objectId, Node task, String status, UUID actor, String actorType) {
        int changed = jdbc.update(
                """
                update tasks set status=?,manually_paused=false,
                    completed_at=case when ?='DONE' then clock_timestamp() else null end,
                    version=version+1,updated_at=clock_timestamp()
                where id=? and object_id=? and version=? and status=?
                """,
                status,
                status,
                task.id(),
                objectId,
                task.version(),
                task.status());
        if (changed != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task changed; reload before continuing");
        jdbc.update(
                """
                insert into task_history(task_id,actor_id,actor_type,previous_status,new_status)
                values (?,?,?,?,?)
                """,
                task.id(),
                actor,
                actorType,
                task.status(),
                status);
        return new TaskStatusChanged(
                objectId,
                actor,
                task.state(),
                new TaskStatusChanged.State(task.id(), task.name(), status, task.version() + 1));
    }

    public void setState(UUID objectId, UUID setId, String state) {
        jdbc.update(
                """
                update task_sets set state=?,version=version+1,updated_at=clock_timestamp()
                where id=? and object_id=? and state='ACTIVE'
                """,
                state,
                setId,
                objectId);
    }
}
