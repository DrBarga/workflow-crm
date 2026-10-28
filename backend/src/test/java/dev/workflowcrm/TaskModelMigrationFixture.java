package dev.workflowcrm;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

class TaskModelMigrationFixture extends ObjectContactMigrationFixture {
    TaskModelMigrationFixture(Connection connection) {
        super(connection);
    }

    @Override
    void seed() {
        super.seed();
        UUID template = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update(
                "insert into task_templates(id,trigger_status_id,contract_type) select ?,id,'Основний' from object_statuses where code='FOUND'",
                template);
        jdbc.update(
                "insert into task_template_items(id,template_id,name,assignee_id,deadline_offset_days,sort_order) values(?,?,'Перевірка',?,3,0)",
                item,
                template,
                manager);
        for (String state : List.of("NEW", "DONE", "CANCELLED"))
            jdbc.update(
                    "insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id) select ?,id,?,'Історична назва',?,current_date,?,? from object_statuses where code='FOUND'",
                    object,
                    item,
                    manager,
                    state,
                    manager);
        jdbc.update(
                "update task_templates set trigger_status_id=(select id from object_statuses where code='IN_PROGRESS') where id=?",
                template);
        jdbc.update(
                "insert into task_templates(trigger_status_id,contract_type) select id,'PRIMARY' from object_statuses where code='IN_PROGRESS'");
    }
}
