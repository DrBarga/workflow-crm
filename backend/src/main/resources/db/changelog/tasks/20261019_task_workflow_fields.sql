ALTER TABLE tasks
    ADD COLUMN author_type varchar(8) NOT NULL DEFAULT 'USER' CHECK(author_type IN ('USER','SYSTEM')),
    ADD COLUMN needs_review boolean NOT NULL DEFAULT true,
    ADD COLUMN result_recipient_id uuid REFERENCES users(id) ON DELETE RESTRICT;
UPDATE tasks SET author_type='SYSTEM' WHERE template_item_id IS NOT NULL OR source_item_id IS NOT NULL;
CREATE INDEX ix_tasks_result_recipient ON tasks(result_recipient_id);

ALTER TABLE task_template_items
    ADD COLUMN needs_review boolean NOT NULL DEFAULT true,
    ADD COLUMN result_recipient_role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    ADD COLUMN escalation_days integer CHECK(escalation_days BETWEEN 0 AND 3650);
CREATE INDEX ix_task_template_items_recipient_role ON task_template_items(result_recipient_role_id);

ALTER TABLE template_items
    ADD COLUMN needs_review boolean NOT NULL DEFAULT true,
    ADD COLUMN result_recipient_role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    ADD COLUMN escalation_days integer CHECK(escalation_days BETWEEN 0 AND 3650);
CREATE INDEX ix_template_items_recipient_role ON template_items(result_recipient_role_id);

UPDATE task_statuses SET name='Завершена' WHERE code='DONE';
