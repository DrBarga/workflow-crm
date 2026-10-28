package dev.workflowcrm.tasks.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.tasks.api.TaskTransitionPlan;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskTemplateSnapshot {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcTaskTemplateSnapshot(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    UUID capture(ObjectTaskTransition.Context context, TaskTransitionPlan plan) {
        var definition = jdbc.queryForObject(
                """
                select coalesce(t.contract_type_id,c.id) as contract_id,t.contract_type from task_templates t
                left join lateral (select id from contract_types where code=t.contract_type or name=t.contract_type
                    order by case when code=t.contract_type then 0 else 1 end,id limit 1) c on true
                where t.id=? for share of t
                """,
                (row, n) -> TaskTemplateDefinition.from(
                        context, plan, row.getObject("contract_id", UUID.class), row.getString("contract_type")),
                plan.templateId());
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(?::text,5804))",
                plan.templateId().toString());
        String hash = definition.fingerprint(json);
        var existing = jdbc.queryForList(
                "select id from task_template_versions where template_id=? and fingerprint=?",
                UUID.class,
                plan.templateId(),
                hash);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID version = UUID.randomUUID();
        jdbc.update(
                """
                insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id,
                    contract_type_id,legacy_contract_type)
                values(?,?,(select coalesce(max(revision),0)+1 from task_template_versions where template_id=?),?,?,?,?)
                """,
                version,
                plan.templateId(),
                plan.templateId(),
                hash,
                definition.triggerStatusId(),
                definition.contractTypeId(),
                definition.legacyContractType());
        jdbc.batchUpdate(
                """
                insert into template_items(template_version_id,source_item_id,name,deadline_rule,
                    deadline_offset_days,assignment_rule,assignee_id,sort_order,needs_review,
                    result_recipient_role_id,escalation_days,role_id,code) values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                definition.items().stream()
                        .map(item -> new Object[] {
                            version,
                            item.id(),
                            item.name(),
                            item.deadline().basis().name(),
                            item.deadline().offsetDays(),
                            item.assignment().kind().name(),
                            item.assignment().userId(),
                            item.sortOrder(),
                            item.needsReview(),
                            item.resultRecipientRoleId(),
                            item.escalationDays(),
                            item.assignment().roleId(),
                            item.code()
                        })
                        .toList());
        jdbc.batchUpdate(
                """
                insert into template_item_dependencies(template_version_id,item_id,predecessor_id)
                select ?,item.id,predecessor.id from template_items item join template_items predecessor
                    on predecessor.template_version_id=item.template_version_id
                where item.template_version_id=? and item.source_item_id=? and predecessor.source_item_id=?
                """,
                versionDependencies(definition, version));
        return version;
    }

    private java.util.List<Object[]> versionDependencies(TaskTemplateDefinition definition, UUID version) {
        return definition.items().stream()
                .flatMap(item -> item.predecessors().stream()
                        .map(predecessor -> new Object[] {version, version, item.id(), predecessor}))
                .toList();
    }
}
