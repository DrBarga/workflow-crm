package dev.workflowcrm;

import java.util.*;

abstract class ObjectStatusTasksSupport extends ObjectStatusTestSupport {
    UUID task(String state) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into tasks(id,object_id,source_status_id,name,assignee_id,deadline,status,author_id)
                select ?,?,status_id,'Статусна задача',?,'2026-10-04',?,? from objects where id=?
                """,
                id,
                object,
                manager,
                state,
                manager,
                object);
        return id;
    }

    UUID template(String target, String contractType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_templates(id,trigger_status_id,contract_type) values (?,?,?)",
                id,
                statusId(target),
                contractType);
        jdbc.update(
                """
                insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order)
                values (?,'Перевірити об’єкт',?,3,0)
                """,
                id,
                manager);
        return id;
    }

    List<String> taskSnapshot() {
        return jdbc.queryForList(
                "select to_jsonb(t)::text from tasks t where object_id=? order by id", String.class, object);
    }

    int events(String type) {
        return jdbc.queryForObject("select count(*) from event_outbox where event_type=?", Integer.class, type);
    }
}
