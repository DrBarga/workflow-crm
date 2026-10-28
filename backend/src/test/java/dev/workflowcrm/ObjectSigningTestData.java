package dev.workflowcrm;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ObjectSigningTestData {
    private ObjectSigningTestData() {}

    static void ready(JdbcTemplate jdbc, UUID object, UUID manager) {
        jdbc.update(
                """
                update objects set commissioning_date='2026-11-01',construction_acceptance_planned_date='2026-11-02',
                    construction_term_days=0 where id=?
                """,
                object);
        UUID template = UUID.randomUUID();
        jdbc.update(
                "insert into task_templates(id,trigger_status_id,is_active) select ?,id,false from object_statuses where code='CONTRACT_APPROVAL'",
                template);
        int order = 0;
        for (String code : List.of("APPROVE_LOTUS_CONTRACT", "APPROVE_FINANCIAL_MODEL", "APPROVE_QUESTIONNAIRE")) {
            UUID item = UUID.randomUUID();
            jdbc.update(
                    "insert into task_template_items(id,template_id,name,code,assignee_id,deadline_offset_days,sort_order) values(?,?,?,?,?,0,?)",
                    item,
                    template,
                    code,
                    code,
                    manager,
                    order++);
            jdbc.update(
                    """
                    insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id)
                    select ?,id,?,?,?,current_date,'DONE',? from object_statuses where code='CONTRACT_APPROVAL'
                    """,
                    object,
                    item,
                    code,
                    manager,
                    manager);
        }
    }
}
