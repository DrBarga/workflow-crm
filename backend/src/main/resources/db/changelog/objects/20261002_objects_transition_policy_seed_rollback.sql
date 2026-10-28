DELETE FROM object_status_transitions WHERE code IN
    ('LEAD_IN_PROGRESS_TO_FOUND', 'LEAD_RESTORE_REFUSED_FOUND', 'LEAD_RESTORE_REFUSED_IN_PROGRESS',
     'LEAD_RESTORE_REFUSED_PAUSED', 'LEAD_REFUSE_OPENED', 'PAUSE_OPENED', 'RESUME_OPENED',
     'CONTRACT_REFUSE_PAUSED', 'MANAGER_RESUME_REFUSED');

DELETE FROM object_transition_roles permission USING object_status_transitions transition, roles role
WHERE permission.transition_id = transition.id AND permission.role_id = role.id
  AND transition.code IN ('LEAD_REFUSE_CONTRACT', 'LEAD_REFUSE_SIGNED') AND role.code = 'CONTRACT_MANAGER';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE (transition.code IN ('MANAGER_IN_PROGRESS_TO_FOUND', 'MANAGER_REFUSED_TO_FOUND',
                          'MANAGER_REFUSED_TO_IN_PROGRESS', 'MANAGER_REFUSED_TO_PAUSED')
       AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR'))
   OR (transition.code = 'RESUME_REFUSED' AND role.code = 'MANAGER');

UPDATE object_status_transitions SET condition_code = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('MANAGER_REFUSED_TO_FOUND', 'MANAGER_REFUSED_TO_IN_PROGRESS', 'MANAGER_REFUSED_TO_PAUSED');

UPDATE object_status_transitions SET is_rollback = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'MANAGER_IN_PROGRESS_TO_FOUND';

UPDATE object_status_transitions SET unfinished_task_action = 'NONE', is_unavailable_after_task_creation = false,
    version = version + 1, updated_at = CURRENT_TIMESTAMP;

UPDATE refusal_reasons SET is_system_only = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'IC_NOT_APPROVED';

UPDATE object_statuses SET is_task_generation_trigger = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('IN_PROGRESS', 'IC_PREPARATION', 'COMMERCIAL_TERMS_APPROVAL', 'CONTRACT_APPROVAL', 'CONTRACT_SIGNED');
