ALTER TABLE object_status_transitions
    DROP CONSTRAINT ck_object_transitions_task_action,
    DROP CONSTRAINT ck_object_transitions_condition,
    DROP COLUMN unfinished_task_action,
    DROP COLUMN is_unavailable_after_task_creation;
ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_condition CHECK
    (condition_code IS NULL OR condition_code IN ('RESUME_PREVIOUS_STATUS', 'EARLY_STATUS_BEFORE_PAUSE'));
ALTER TABLE refusal_reasons DROP COLUMN is_system_only;
DROP INDEX ix_objects_status_before_refusal;
ALTER TABLE objects DROP COLUMN status_before_refusal_id;
