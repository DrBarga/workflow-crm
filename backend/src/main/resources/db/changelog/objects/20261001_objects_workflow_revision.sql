ALTER TABLE object_status_transitions
    ADD COLUMN condition_code varchar(32),
    ADD COLUMN fixed_refusal_reason_id uuid REFERENCES refusal_reasons(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_object_transitions_condition CHECK
        (condition_code IS NULL OR condition_code IN ('RESUME_PREVIOUS_STATUS', 'EARLY_STATUS_BEFORE_PAUSE')),
    ADD CONSTRAINT ck_object_transitions_fixed_reason CHECK
        (fixed_refusal_reason_id IS NULL OR investment_committee_decision IS NOT DISTINCT FROM 'REFUSED');
CREATE INDEX ix_object_transitions_fixed_reason ON object_status_transitions(fixed_refusal_reason_id);

UPDATE object_statuses SET is_final = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'REFUSED';

UPDATE object_status_transitions SET is_active = true, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'OPEN_OBJECT';

UPDATE object_status_transitions SET is_active = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'REQUEST_PAUSED_TO_IC';

UPDATE object_status_transitions
SET condition_code = 'RESUME_PREVIOUS_STATUS', version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('MANAGER_PAUSED_TO_FOUND', 'MANAGER_PAUSED_TO_IN_PROGRESS', 'LEAD_PAUSED_TO_IC');

UPDATE object_status_transitions
SET condition_code = 'EARLY_STATUS_BEFORE_PAUSE', version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'MANAGER_PAUSED_TO_REFUSED';

UPDATE object_status_transitions
SET fixed_refusal_reason_id = (SELECT id FROM refusal_reasons WHERE code = 'IC_NOT_APPROVED'),
    version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'IC_REFUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE (transition.code LIKE 'MANAGER_%' AND transition.code <> 'MANAGER_PAUSED_TO_REFUSED'
       AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'))
   OR (transition.code IN ('LEAD_FOUND_TO_IC', 'LEAD_IN_PROGRESS_TO_IC', 'LEAD_PAUSED_TO_IC',
                          'REQUEST_CONTRACT_APPROVAL', 'SIGN_CONTRACT') AND role.code = 'DEVELOPMENT_DIRECTOR')
   OR (transition.code = 'OPEN_OBJECT' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'));

INSERT INTO object_status_transitions(code, from_status_id, to_status_id, condition_code)
SELECT rule.code, source.id, target.id, rule.condition_code
FROM (VALUES
    ('LEAD_REFUSE_IC', 'IC_PREPARATION', 'REFUSED', NULL),
    ('LEAD_REFUSE_COMMERCIAL', 'COMMERCIAL_TERMS_APPROVAL', 'REFUSED', NULL),
    ('LEAD_REFUSE_CONTRACT', 'CONTRACT_APPROVAL', 'REFUSED', NULL),
    ('LEAD_REFUSE_SIGNED', 'CONTRACT_SIGNED', 'REFUSED', NULL),
    ('LEAD_REFUSE_PAUSED', 'PAUSED', 'REFUSED', NULL),
    ('RESTORE_REFUSED_IC', 'REFUSED', 'IC_PREPARATION', NULL),
    ('RESTORE_REFUSED_COMMERCIAL', 'REFUSED', 'COMMERCIAL_TERMS_APPROVAL', NULL),
    ('RESTORE_REFUSED_CONTRACT', 'REFUSED', 'CONTRACT_APPROVAL', NULL),
    ('RESTORE_REFUSED_SIGNED', 'REFUSED', 'CONTRACT_SIGNED', NULL),
    ('RESTORE_REFUSED_OPENED', 'REFUSED', 'OPENED', NULL),
    ('PAUSE_IC', 'IC_PREPARATION', 'PAUSED', NULL),
    ('PAUSE_COMMERCIAL', 'COMMERCIAL_TERMS_APPROVAL', 'PAUSED', NULL),
    ('PAUSE_CONTRACT', 'CONTRACT_APPROVAL', 'PAUSED', NULL),
    ('PAUSE_SIGNED', 'CONTRACT_SIGNED', 'PAUSED', NULL),
    ('RESUME_COMMERCIAL', 'PAUSED', 'COMMERCIAL_TERMS_APPROVAL', 'RESUME_PREVIOUS_STATUS'),
    ('RESUME_CONTRACT', 'PAUSED', 'CONTRACT_APPROVAL', 'RESUME_PREVIOUS_STATUS'),
    ('RESUME_SIGNED', 'PAUSED', 'CONTRACT_SIGNED', 'RESUME_PREVIOUS_STATUS'),
    ('RESUME_REFUSED', 'PAUSED', 'REFUSED', 'RESUME_PREVIOUS_STATUS')
) rule(code, from_code, to_code, condition_code)
JOIN object_statuses source ON source.code = rule.from_code
JOIN object_statuses target ON target.code = rule.to_code;

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE (transition.code LIKE 'LEAD_REFUSE_%' OR transition.code LIKE 'RESTORE_REFUSED_%'
       OR transition.code LIKE 'PAUSE_%' OR transition.code LIKE 'RESUME_%')
  AND (role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR')
       OR (transition.code = 'RESUME_REFUSED' AND role.code = 'MANAGER'));

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code
FROM object_status_transitions transition
JOIN (VALUES
    ('LEAD_REFUSE_IC', 'REFUSAL_REASON'),
    ('LEAD_REFUSE_COMMERCIAL', 'REFUSAL_REASON'),
    ('LEAD_REFUSE_CONTRACT', 'REFUSAL_REASON'),
    ('LEAD_REFUSE_SIGNED', 'REFUSAL_REASON'),
    ('LEAD_REFUSE_PAUSED', 'REFUSAL_REASON'),
    ('RESTORE_REFUSED_SIGNED', 'CONTRACT_SIGNED_DATE'),
    ('RESTORE_REFUSED_OPENED', 'CONTRACT_SIGNED_DATE'),
    ('RESTORE_REFUSED_OPENED', 'OPENING_DATE')
) required(code, field_code) ON required.code = transition.code;
