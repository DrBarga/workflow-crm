ALTER TABLE object_status_transitions DROP CONSTRAINT ck_object_transitions_task_action;
ALTER TABLE object_status_transitions ALTER COLUMN unfinished_task_action TYPE varchar(16);
ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_task_action
    CHECK (unfinished_task_action IN ('BLOCK', 'CANCEL', 'CANCEL_ALL', 'NONE'));
