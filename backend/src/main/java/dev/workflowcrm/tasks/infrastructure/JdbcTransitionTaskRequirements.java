package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.TransitionTaskRequirements;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTransitionTaskRequirements implements TransitionTaskRequirements {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    JdbcTransitionTaskRequirements(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Set<String> missing(UUID objectId, UUID statusId, Set<String> codes) {
        return requirements(objectId, statusId, codes).missingCodes();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Assessment requirements(UUID objectId, UUID statusId, Set<String> codes) {
        if (codes.isEmpty()) return new Assessment(Set.of(), List.of());
        jdbc.queryForList(
                "select id from tasks where object_id=? and source_status_id=? order by id for share",
                UUID.class,
                objectId,
                statusId);
        return assess(objectId, statusId, codes);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Assessment inspect(UUID objectId, UUID statusId, Set<String> codes) {
        return assess(objectId, statusId, codes);
    }

    private Assessment assess(UUID objectId, UUID statusId, Set<String> codes) {
        var missing = new TreeSet<>(codes);
        if (codes.isEmpty()) return new Assessment(Set.of(), List.of());
        var latest = named.query(
                """
                with candidates as (
                    select i.code,t.id,t.name,t.status,s.created_at as set_created,s.id as set_id,t.created_at as task_created,1 as set_priority
                    from task_sets s join template_items i on i.template_version_id=s.template_version_id
                    left join tasks t on t.task_set_id=s.id and t.source_item_id=i.id
                    where s.object_id=:object and s.trigger_status_id=:status and i.code in (:codes)
                    union all
                    select d.code,t.id,t.name,t.status,coalesce(s.created_at,t.created_at),s.id,t.created_at,case when s.id is null then 0 else 1 end
                    from tasks t left join task_sets s on s.id=t.task_set_id
                    join task_template_items d on d.id=t.template_item_id
                    where t.object_id=:object and t.source_status_id=:status and t.source_item_id is null
                        and d.code in (:codes)
                )
                , ranked as (
                    select candidates.*,dense_rank() over (
                        partition by code order by set_priority desc,
                        case when set_id is not null then set_created end desc nulls last,set_id desc nulls last
                    ) as set_rank from candidates
                )
                select code,id,name,status from ranked where set_rank=1
                order by code,task_created,id
                """,
                Map.of("object", objectId, "status", statusId, "codes", codes),
                (row, n) -> new Task(
                        row.getObject("id", UUID.class),
                        row.getString("code"),
                        row.getString("name"),
                        row.getString("status")));
        latest.stream()
                .collect(java.util.stream.Collectors.groupingBy(Task::code))
                .forEach((code, tasks) -> {
                    if (tasks.stream().allMatch(task -> "DONE".equals(task.status()))) missing.remove(code);
                });
        var incomplete = latest.stream()
                .filter(t -> t.id() != null && !"DONE".equals(t.status()))
                .toList();
        return new Assessment(java.util.Collections.unmodifiableSet(missing), incomplete);
    }
}
