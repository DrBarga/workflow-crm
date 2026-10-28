package dev.workflowcrm.tasks.infrastructure;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTemplateConfigurationException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskTemplateResolver {
    private final JdbcTemplate jdbc;

    JdbcTaskTemplateResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Template> find(ObjectTaskTransition.Context context) {
        String type = context.contractType();
        if (type != null) type = type.isBlank() ? null : type.strip();
        var exact = find(context, type);
        if (type == null) return exact;
        boolean populated = exact.isPresent()
                && Boolean.TRUE.equals(jdbc.queryForObject(
                        "select exists(select 1 from task_template_items where template_id=?)",
                        Boolean.class,
                        exact.get().id()));
        return populated ? exact : find(context, null);
    }

    private Optional<Template> find(ObjectTaskTransition.Context context, String type) {
        var templates = jdbc.query(
                """
                select t.id,t.version from task_templates t
                left join contract_types c on c.id=t.contract_type_id
                where t.trigger_status_id=? and t.is_active
                and ((?::varchar is null and t.contract_type_id is null and t.contract_type is null)
                    or (?::varchar is not null and (
                        (t.contract_type_id is not null and (c.code=? or c.name=?))
                        or (t.contract_type_id is null and (t.contract_type=? or exists(
                            select 1 from contract_types r where (r.code=? or r.name=?)
                                and (t.contract_type=r.code or t.contract_type=r.name)))))))
                order by t.id limit 2 for share of t
                """,
                (row, n) -> new Template(row.getObject("id", UUID.class), row.getLong("version")),
                context.toStatusId(),
                type,
                type,
                type,
                type,
                type,
                type,
                type);
        if (templates.size() > 1) throw new TaskTemplateConfigurationException();
        return templates.stream().findFirst();
    }

    record Template(UUID id, long version) {}
}
