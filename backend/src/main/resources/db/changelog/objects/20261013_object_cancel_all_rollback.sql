ALTER TABLE object_status_transitions DROP CONSTRAINT ck_object_transitions_task_action;
UPDATE object_status_transitions SET unfinished_task_action='CANCEL', version=version+1,
    updated_at=CURRENT_TIMESTAMP WHERE unfinished_task_action='CANCEL_ALL';
ALTER TABLE object_status_transitions ALTER COLUMN unfinished_task_action TYPE varchar(8);
ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_task_action
    CHECK (unfinished_task_action IN ('BLOCK', 'CANCEL', 'NONE'));
