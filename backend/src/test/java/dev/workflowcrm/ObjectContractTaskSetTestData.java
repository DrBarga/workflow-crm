package dev.workflowcrm;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ObjectContractTaskSetTestData {
    private ObjectContractTaskSetTestData() {}

    static UUID create(JdbcTemplate jdbc, UUID object, UUID contract, String legacy, String state) {
        UUID status = jdbc.queryForObject("select status_id from objects where id=?", UUID.class, object);
        var templates = jdbc.queryForList(
                "select id from task_templates where trigger_status_id=? and contract_type is null order by id limit 1",
                UUID.class,
                status);
        UUID template = templates.isEmpty() ? UUID.randomUUID() : templates.getFirst();
        UUID revision = UUID.randomUUID();
        UUID set = UUID.randomUUID();
        if (templates.isEmpty())
            jdbc.update(
                    "insert into task_templates(id,trigger_status_id,is_active) values(?,?,false)", template, status);
        jdbc.update(
                "update task_templates set contract_type_id=?,contract_type=? where id=?", contract, legacy, template);
        jdbc.update(
                """
                insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id,
                    contract_type_id,legacy_contract_type) values(?,?,1,?,?,?,?)
                """,
                revision,
                template,
                "test:" + revision,
                status,
                contract,
                legacy);
        jdbc.update(
                """
                insert into task_sets(id,object_id,template_version_id,trigger_status_id,state) values(?,?,?,?,?)
                """,
                set,
                object,
                revision,
                status,
                state);
        return set;
    }
}
