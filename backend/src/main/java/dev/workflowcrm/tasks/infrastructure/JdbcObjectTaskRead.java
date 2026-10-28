package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.shared.read.ReadPage;
import dev.workflowcrm.tasks.api.ObjectTaskRead;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcObjectTaskRead implements ObjectTaskRead {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcObjectTaskRead(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page list(UUID objectId, UUID taskSetId, ReadPage page) {
        var p = new HashMap<String, Object>();
        p.put("object", objectId);
        p.put("set", taskSetId);
        p.put("limit", page.size());
        p.put("offset", page.offset());
        String where = "where t.object_id=:object and (cast(:set as uuid) is null or t.task_set_id=:set)";
        long total = jdbc.queryForObject("select count(*) from tasks t " + where, p, Long.class);
        var rows = jdbc.query(
                """
                select t.id,t.name,t.assignee_id,u.full_name,t.deadline,t.status,s.name status_name,t.task_set_id,
                    t.needs_review,t.manually_paused,t.version from tasks t
                join users u on u.id=t.assignee_id join task_statuses s on s.code=t.status
                """
                        + where + " order by t.created_at,t.id limit :limit offset :offset",
                p,
                (r, n) -> new Task(
                        r.getObject("id", UUID.class),
                        r.getString("name"),
                        new Assignee(r.getObject("assignee_id", UUID.class), r.getString("full_name")),
                        r.getDate("deadline").toLocalDate(),
                        r.getString("status"),
                        r.getString("status_name"),
                        r.getObject("task_set_id", UUID.class),
                        r.getBoolean("needs_review"),
                        r.getBoolean("manually_paused"),
                        r.getLong("version"),
                        List.of()));
        if (rows.isEmpty()) return new Page(List.of(), List.of(), page.page(), page.size(), total, false);
        p.put("ids", rows.stream().map(Task::id).toList());
        var dependencies = new HashMap<UUID, List<Predecessor>>();
        jdbc.query(
                """
                select d.task_id,p.id,p.name,p.status from task_dependencies d
                join tasks p on p.id=d.predecessor_id and p.object_id=d.object_id
                where d.object_id=:object and d.task_id in (:ids) order by d.task_id,p.id
                """,
                p,
                (org.springframework.jdbc.core.RowCallbackHandler) r -> dependencies
                        .computeIfAbsent(r.getObject("task_id", UUID.class), key -> new ArrayList<>())
                        .add(new Predecessor(
                                r.getObject("id", UUID.class), r.getString("name"), r.getString("status"))));
        var items = rows.stream()
                .map(t -> new Task(
                        t.id(),
                        t.name(),
                        t.assignee(),
                        t.deadline(),
                        t.status(),
                        t.statusName(),
                        t.taskSetId(),
                        t.needsReview(),
                        t.manuallyPaused(),
                        t.version(),
                        dependencies.getOrDefault(t.id(), List.of())))
                .toList();
        var sets = rows.stream()
                .map(Task::taskSetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<TaskSet> metadata = List.of();
        if (!sets.isEmpty()) {
            p.put("sets", sets);
            metadata = jdbc.query(
                    """
                    select ts.id,s.code,ts.state,ts.created_at from task_sets ts
                    join object_statuses s on s.id=ts.trigger_status_id where ts.object_id=:object and ts.id in (:sets)
                    order by ts.created_at,ts.id
                    """,
                    p,
                    (r, n) -> new TaskSet(
                            r.getObject("id", UUID.class),
                            r.getString("code"),
                            r.getString("state"),
                            r.getTimestamp("created_at").toInstant()));
        }
        return new Page(items, metadata, page.page(), page.size(), total, page.offset() + items.size() < total);
    }
}
