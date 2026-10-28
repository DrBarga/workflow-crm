ALTER TABLE objects ADD COLUMN status_before_refusal_id uuid
    REFERENCES object_statuses(id) ON DELETE RESTRICT;
CREATE INDEX ix_objects_status_before_refusal ON objects(status_before_refusal_id);

ALTER TABLE refusal_reasons ADD COLUMN is_system_only boolean NOT NULL DEFAULT false;

ALTER TABLE object_status_transitions
    ADD COLUMN unfinished_task_action varchar(8) NOT NULL DEFAULT 'NONE',
    ADD COLUMN is_unavailable_after_task_creation boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_object_transitions_task_action CHECK (unfinished_task_action IN ('BLOCK', 'CANCEL', 'NONE')),
    DROP CONSTRAINT ck_object_transitions_condition;

ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_condition CHECK
    (condition_code IS NULL OR condition_code IN ('RESUME_PREVIOUS_STATUS', 'EARLY_STATUS_BEFORE_PAUSE',
     'EARLY_STATUS_BEFORE_REFUSAL', 'CONTRACT_STATUS_BEFORE_PAUSE', 'RESUME_EARLY_REFUSED'));
