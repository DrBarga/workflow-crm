ALTER TABLE task_template_items
    ADD COLUMN deadline_rule varchar(24) NOT NULL DEFAULT 'FROM_START_CALENDAR'
        CONSTRAINT ck_task_template_item_deadline_rule CHECK (deadline_rule = 'FROM_START_CALENDAR');
