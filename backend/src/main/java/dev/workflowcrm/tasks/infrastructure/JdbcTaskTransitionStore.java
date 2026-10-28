package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import dev.workflowcrm.tasks.domain.TaskTransitionStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskTransitionStore implements TaskTransitionStore {
    private final JdbcTemplate jdbc;
    private final JdbcTaskTemplateQuery templates;
    private final JdbcTaskCancellation cancellation;
    private final JdbcTaskSetGeneration generation;

    JdbcTaskTransitionStore(
            JdbcTemplate jdbc,
            JdbcTaskTemplateQuery templates,
            JdbcTaskCancellation cancellation,
            JdbcTaskSetGeneration generation) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.cancellation = cancellation;
        this.generation = generation;
    }

    public Optional<TaskTransitionPlan> template(ObjectTaskTransition.Context context) {
        return templates.find(context);
    }

    public List<TaskTransitionPlan.ExistingTask> unfinished(ObjectTaskTransition.Context context) {
        return jdbc.query(
                """
                select t.id, t.version, t.name, t.status, t.assignee_id from tasks t
                where t.object_id = ? and (? or (t.source_status_id = ? and (not ? or exists (
                    select 1 from task_sets s where s.id=t.task_set_id and s.state='ACTIVE'))))
                and t.status not in ('DONE', 'CANCELLED') order by t.id for update of t
                """,
                (row, n) -> new TaskTransitionPlan.ExistingTask(
                        row.getObject("id", UUID.class),
                        row.getLong("version"),
                        row.getString("name"),
                        row.getString("status"),
                        row.getObject("assignee_id", UUID.class)),
                context.objectId(),
                context.allObjectTasks(),
                context.fromStatusId(),
                context.activeSourceSetOnly());
    }

    public void cancel(List<TaskTransitionPlan.ExistingTask> tasks, UUID actor) {
        cancellation.cancel(tasks, actor);
    }

    public boolean hasTasks(UUID objectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from task_sets where object_id=?) or exists(select 1 from tasks where object_id=?)",
                Boolean.class,
                objectId,
                objectId));
    }

    public boolean hasContractTypeTaskSet(UUID objectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select exists(select 1 from task_sets s
                join task_template_versions v on v.id=s.template_version_id
                where s.object_id=? and (v.contract_type_id is not null or v.legacy_contract_type is not null))
                """,
                Boolean.class,
                objectId));
    }

    public boolean hasActiveSet(ObjectTaskTransition.Context context) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from task_sets where object_id=? and trigger_status_id=? and state='ACTIVE')",
                Boolean.class,
                context.objectId(),
                context.toStatusId()));
    }

    public UUID generate(ObjectTaskTransition.Context context, TaskTransitionPlan plan, UUID actor) {
        return generation.generate(context, plan, actor);
    }
}
