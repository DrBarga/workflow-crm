UPDATE object_statuses SET is_task_generation_trigger = true, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('IN_PROGRESS', 'IC_PREPARATION', 'COMMERCIAL_TERMS_APPROVAL', 'CONTRACT_APPROVAL', 'CONTRACT_SIGNED');

UPDATE refusal_reasons SET is_system_only = true, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'IC_NOT_APPROVED';

UPDATE object_status_transitions SET is_rollback = true, is_unavailable_after_task_creation = true,
    version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'MANAGER_IN_PROGRESS_TO_FOUND';

UPDATE object_status_transitions SET condition_code = 'EARLY_STATUS_BEFORE_REFUSAL',
    version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('MANAGER_REFUSED_TO_FOUND', 'MANAGER_REFUSED_TO_IN_PROGRESS', 'MANAGER_REFUSED_TO_PAUSED');

DELETE FROM object_transition_roles permission USING object_status_transitions transition, roles role
WHERE permission.transition_id = transition.id AND permission.role_id = role.id
  AND ((transition.code IN ('MANAGER_IN_PROGRESS_TO_FOUND', 'MANAGER_REFUSED_TO_FOUND',
                           'MANAGER_REFUSED_TO_IN_PROGRESS', 'MANAGER_REFUSED_TO_PAUSED')
        AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'))
       OR (transition.code = 'RESUME_REFUSED' AND role.code = 'MANAGER'));

INSERT INTO object_status_transitions(code, from_status_id, to_status_id, condition_code, is_rollback)
SELECT rule.code, source.id, target.id, rule.condition_code, rule.is_rollback
FROM (VALUES
    ('LEAD_IN_PROGRESS_TO_FOUND', 'IN_PROGRESS', 'FOUND', NULL, true),
    ('LEAD_RESTORE_REFUSED_FOUND', 'REFUSED', 'FOUND', NULL, false),
    ('LEAD_RESTORE_REFUSED_IN_PROGRESS', 'REFUSED', 'IN_PROGRESS', NULL, false),
    ('LEAD_RESTORE_REFUSED_PAUSED', 'REFUSED', 'PAUSED', NULL, false),
    ('LEAD_REFUSE_OPENED', 'OPENED', 'REFUSED', NULL, false),
    ('PAUSE_OPENED', 'OPENED', 'PAUSED', NULL, false),
    ('RESUME_OPENED', 'PAUSED', 'OPENED', 'RESUME_PREVIOUS_STATUS', false),
    ('CONTRACT_REFUSE_PAUSED', 'PAUSED', 'REFUSED', 'CONTRACT_STATUS_BEFORE_PAUSE', false),
    ('MANAGER_RESUME_REFUSED', 'PAUSED', 'REFUSED', 'RESUME_EARLY_REFUSED', false)
) rule(code, from_code, to_code, condition_code, is_rollback)
JOIN object_statuses source ON source.code = rule.from_code
JOIN object_statuses target ON target.code = rule.to_code;

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE (transition.code IN ('LEAD_IN_PROGRESS_TO_FOUND', 'LEAD_RESTORE_REFUSED_FOUND',
                          'LEAD_RESTORE_REFUSED_IN_PROGRESS', 'LEAD_RESTORE_REFUSED_PAUSED',
                          'LEAD_REFUSE_OPENED', 'PAUSE_OPENED', 'RESUME_OPENED')
       AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'))
   OR (transition.code IN ('LEAD_REFUSE_CONTRACT', 'LEAD_REFUSE_SIGNED', 'LEAD_REFUSE_OPENED',
                          'CONTRACT_REFUSE_PAUSED') AND role.code = 'CONTRACT_MANAGER')
   OR (transition.code = 'MANAGER_RESUME_REFUSED' AND role.code = 'MANAGER');

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT id, 'REFUSAL_REASON' FROM object_status_transitions
WHERE code IN ('LEAD_REFUSE_OPENED', 'CONTRACT_REFUSE_PAUSED');

UPDATE object_status_transitions transition
SET unfinished_task_action = CASE
        WHEN condition_code IN ('RESUME_PREVIOUS_STATUS', 'RESUME_EARLY_REFUSED') THEN 'NONE'
        WHEN source.code = 'REFUSED' OR target.code = 'PAUSED' THEN 'NONE'
        WHEN target.code = 'REFUSED' OR is_rollback OR is_system_only THEN 'CANCEL'
        ELSE 'BLOCK'
    END,
    version = transition.version + 1, updated_at = CURRENT_TIMESTAMP
FROM object_statuses source, object_statuses target
WHERE transition.from_status_id = source.id AND transition.to_status_id = target.id;
