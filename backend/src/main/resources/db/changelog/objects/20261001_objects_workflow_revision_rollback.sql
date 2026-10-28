DELETE FROM object_status_transitions WHERE code IN (
    'LEAD_REFUSE_IC', 'LEAD_REFUSE_COMMERCIAL', 'LEAD_REFUSE_CONTRACT', 'LEAD_REFUSE_SIGNED', 'LEAD_REFUSE_PAUSED',
    'RESTORE_REFUSED_IC', 'RESTORE_REFUSED_COMMERCIAL', 'RESTORE_REFUSED_CONTRACT', 'RESTORE_REFUSED_SIGNED', 'RESTORE_REFUSED_OPENED',
    'PAUSE_IC', 'PAUSE_COMMERCIAL', 'PAUSE_CONTRACT', 'PAUSE_SIGNED',
    'RESUME_COMMERCIAL', 'RESUME_CONTRACT', 'RESUME_SIGNED', 'RESUME_REFUSED'
);

DELETE FROM object_transition_roles permission USING object_status_transitions transition, roles role
WHERE permission.transition_id = transition.id AND permission.role_id = role.id
  AND ((transition.code LIKE 'MANAGER_%' AND transition.code <> 'MANAGER_PAUSED_TO_REFUSED'
        AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'))
    OR (transition.code IN ('LEAD_FOUND_TO_IC', 'LEAD_IN_PROGRESS_TO_IC', 'LEAD_PAUSED_TO_IC',
                           'REQUEST_CONTRACT_APPROVAL', 'SIGN_CONTRACT') AND role.code = 'DEVELOPMENT_DIRECTOR')
    OR transition.code = 'OPEN_OBJECT');

UPDATE object_status_transitions SET is_active = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'OPEN_OBJECT';
UPDATE object_status_transitions SET is_active = true, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'REQUEST_PAUSED_TO_IC';
UPDATE object_statuses SET is_final = true, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'REFUSED';

DROP INDEX ix_object_transitions_fixed_reason;
ALTER TABLE object_status_transitions
    DROP CONSTRAINT ck_object_transitions_condition,
    DROP CONSTRAINT ck_object_transitions_fixed_reason,
    DROP COLUMN condition_code,
    DROP COLUMN fixed_refusal_reason_id;
