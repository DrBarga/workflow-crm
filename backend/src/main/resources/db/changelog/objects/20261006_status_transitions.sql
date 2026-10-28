CREATE TABLE object_transition_execution_backups (
    id uuid PRIMARY KEY REFERENCES object_status_transitions(id) ON DELETE RESTRICT,
    configuration jsonb NOT NULL,
    role_ids uuid[] NOT NULL,
    required_fields text[] NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO object_transition_execution_backups(id,configuration,role_ids,required_fields)
SELECT t.id,to_jsonb(t),
    ARRAY(SELECT role_id FROM object_transition_roles WHERE transition_id=t.id ORDER BY role_id),
    ARRAY(SELECT field_code FROM object_transition_required_fields WHERE transition_id=t.id ORDER BY field_code)
FROM object_status_transitions t;

ALTER TABLE object_status_transitions DROP CONSTRAINT ck_object_transitions_condition;
ALTER TABLE object_status_transitions ADD CONSTRAINT ck_object_transitions_condition CHECK
    (condition_code IS NULL OR condition_code IN ('RESUME_PREVIOUS_STATUS','NOT_RESUME_PREVIOUS_STATUS',
     'EARLY_STATUS_BEFORE_PAUSE','EARLY_STATUS_BEFORE_REFUSAL','CONTRACT_STATUS_BEFORE_PAUSE','RESUME_EARLY_REFUSED'));
UPDATE object_status_transitions SET condition_code='NOT_RESUME_PREVIOUS_STATUS',version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE code='LEAD_REFUSE_PAUSED';

ALTER TABLE object_status_transitions
    ADD COLUMN is_pause_entry boolean NOT NULL DEFAULT false,
    ADD COLUMN is_pause_exit boolean NOT NULL DEFAULT false,
    ADD COLUMN is_refusal_entry boolean NOT NULL DEFAULT false,
    ADD COLUMN is_refusal_exit boolean NOT NULL DEFAULT false,
    ADD COLUMN is_ic_reset boolean NOT NULL DEFAULT false,
    ADD COLUMN is_signing_date_reset boolean NOT NULL DEFAULT false,
    ADD COLUMN is_opening_date_reset boolean NOT NULL DEFAULT false,
    ADD COLUMN milestone_trigger_field varchar(32)
        CHECK (milestone_trigger_field IS NULL OR milestone_trigger_field IN ('CONTRACT_SIGNED_DATE','OPENING_DATE'));

ALTER TABLE object_transition_required_fields DROP CONSTRAINT ck_object_transition_required_field;
ALTER TABLE object_transition_required_fields ADD CONSTRAINT ck_object_transition_required_field CHECK
    (field_code IN ('INVESTMENT_COMMITTEE_DATE','INVESTMENT_COMMITTEE_CONDITIONS','REFUSAL_REASON',
                   'CONTRACT_SIGNED_DATE','OPENING_DATE','CONTRACT_TYPE'));

DELETE FROM object_transition_roles permission USING roles role,object_status_transitions t
WHERE permission.role_id=role.id AND permission.transition_id=t.id
  AND role.code='CONTRACT_MANAGER' AND t.to_status_id=(SELECT id FROM object_statuses WHERE code='REFUSED');
UPDATE object_status_transitions SET is_active=false,version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE code='CONTRACT_REFUSE_PAUSED';
DELETE FROM object_transition_roles permission USING roles role,object_status_transitions t
WHERE permission.role_id=role.id AND permission.transition_id=t.id
  AND role.code IN ('REGIONAL_MANAGER','DEVELOPMENT_DIRECTOR') AND t.code='MANAGER_PAUSED_TO_FOUND';
UPDATE object_status_transitions SET condition_code=NULL,unfinished_task_action='CANCEL',
    is_unavailable_after_task_creation=true,version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE code='MANAGER_PAUSED_TO_FOUND';
INSERT INTO object_status_transitions(code,from_status_id,to_status_id,unfinished_task_action,is_rollback)
SELECT 'LEAD_PAUSED_TO_FOUND',source.id,target.id,'CANCEL',true
FROM object_statuses source,object_statuses target WHERE source.code='PAUSED' AND target.code='FOUND';
INSERT INTO object_transition_roles(transition_id,role_id)
SELECT t.id,r.id FROM object_status_transitions t,roles r
WHERE t.code='LEAD_PAUSED_TO_FOUND' AND r.code IN ('REGIONAL_MANAGER','DEVELOPMENT_DIRECTOR');
INSERT INTO object_transition_required_fields(transition_id,field_code)
SELECT id,'CONTRACT_TYPE' FROM object_status_transitions
WHERE code IN ('SIGN_CONTRACT','RESTORE_REFUSED_SIGNED','RESTORE_REFUSED_OPENED');

UPDATE object_status_transitions t SET
    is_pause_entry=(target.code='PAUSED'),
    is_pause_exit=(source.code='PAUSED'),
    is_refusal_entry=(target.code='REFUSED' AND source.code<>'PAUSED'),
    is_refusal_exit=(source.code='REFUSED' AND target.code<>'PAUSED'),
    is_ic_reset=(target.code='IC_PREPARATION'),
    is_signing_date_reset=(t.is_rollback AND source.code='CONTRACT_SIGNED'),
    is_opening_date_reset=(t.is_rollback AND source.code='OPENED'),
    milestone_trigger_field=CASE WHEN t.code='SIGN_CONTRACT' THEN 'CONTRACT_SIGNED_DATE'
                                WHEN t.code='OPEN_OBJECT' THEN 'OPENING_DATE' ELSE NULL END,
    version=t.version+1,updated_at=CURRENT_TIMESTAMP
FROM object_statuses source,object_statuses target
WHERE source.id=t.from_status_id AND target.id=t.to_status_id;
UPDATE object_status_transitions SET is_refusal_entry=true,version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE code IN ('MANAGER_PAUSED_TO_REFUSED','LEAD_REFUSE_PAUSED','CONTRACT_REFUSE_PAUSED');

CREATE TABLE object_transition_condition_statuses (
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(transition_id,status_id)
);
CREATE INDEX ix_object_transition_condition_status ON object_transition_condition_statuses(status_id);
INSERT INTO object_transition_condition_statuses(transition_id,status_id)
SELECT t.id,s.id FROM object_status_transitions t,object_statuses s
WHERE (t.condition_code IN ('EARLY_STATUS_BEFORE_PAUSE','EARLY_STATUS_BEFORE_REFUSAL','RESUME_EARLY_REFUSED')
       AND s.code IN ('FOUND','IN_PROGRESS'))
   OR (t.condition_code='CONTRACT_STATUS_BEFORE_PAUSE'
       AND s.code IN ('CONTRACT_APPROVAL','CONTRACT_SIGNED','OPENED'));
