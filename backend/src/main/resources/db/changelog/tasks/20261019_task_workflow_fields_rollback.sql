UPDATE task_statuses SET name='Виконана' WHERE code='DONE';
ALTER TABLE template_items
    DROP COLUMN escalation_days, DROP COLUMN result_recipient_role_id, DROP COLUMN needs_review;
ALTER TABLE task_template_items
    DROP COLUMN escalation_days, DROP COLUMN result_recipient_role_id, DROP COLUMN needs_review;
ALTER TABLE tasks
    DROP COLUMN result_recipient_id, DROP COLUMN needs_review, DROP COLUMN author_type;
