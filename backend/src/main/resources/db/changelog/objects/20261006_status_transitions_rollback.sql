DELETE FROM object_transition_required_fields WHERE field_code='CONTRACT_TYPE';
DROP TABLE object_transition_condition_statuses;
DELETE FROM object_transition_roles WHERE transition_id IN
    (SELECT id FROM object_status_transitions WHERE code='LEAD_PAUSED_TO_FOUND');
DELETE FROM object_status_transitions WHERE code='LEAD_PAUSED_TO_FOUND';
UPDATE object_status_transitions t SET
    condition_code=b.configuration->>'condition_code',
    unfinished_task_action=b.configuration->>'unfinished_task_action',
    is_unavailable_after_task_creation=(b.configuration->>'is_unavailable_after_task_creation')::boolean,
    is_rollback=(b.configuration->>'is_rollback')::boolean,
    is_active=(b.configuration->>'is_active')::boolean,
    version=t.version+1,updated_at=CURRENT_TIMESTAMP
FROM object_transition_execution_backups b WHERE t.id=b.id;
ALTER TABLE object_status_transitions DROP CONSTRAINT ck_object_transitions_condition;
ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_condition CHECK
    (condition_code IS NULL OR condition_code IN ('RESUME_PREVIOUS_STATUS','EARLY_STATUS_BEFORE_PAUSE',
     'EARLY_STATUS_BEFORE_REFUSAL','CONTRACT_STATUS_BEFORE_PAUSE','RESUME_EARLY_REFUSED'));
DELETE FROM object_transition_roles WHERE transition_id IN (SELECT id FROM object_transition_execution_backups);
INSERT INTO object_transition_roles(transition_id,role_id)
SELECT id,unnest(role_ids) FROM object_transition_execution_backups;
DROP TABLE object_transition_execution_backups;
ALTER TABLE object_status_transitions
    DROP COLUMN is_pause_entry,DROP COLUMN is_pause_exit,DROP COLUMN is_refusal_entry,
    DROP COLUMN is_refusal_exit,DROP COLUMN is_ic_reset,DROP COLUMN is_signing_date_reset,
    DROP COLUMN is_opening_date_reset,DROP COLUMN milestone_trigger_field;
ALTER TABLE object_transition_required_fields DROP CONSTRAINT ck_object_transition_required_field;
ALTER TABLE object_transition_required_fields ADD CONSTRAINT ck_object_transition_required_field CHECK
    (field_code IN ('INVESTMENT_COMMITTEE_DATE','INVESTMENT_COMMITTEE_CONDITIONS','REFUSAL_REASON',
                   'CONTRACT_SIGNED_DATE','OPENING_DATE'));
