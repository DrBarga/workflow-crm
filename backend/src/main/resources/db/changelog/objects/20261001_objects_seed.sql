INSERT INTO object_statuses(code, name, sort_order, is_final) VALUES
    ('FOUND', 'Знайдено', 10, false),
    ('IN_PROGRESS', 'У роботі', 20, false),
    ('IC_PREPARATION', 'Підготовка на ІК', 30, false),
    ('COMMERCIAL_TERMS_APPROVAL', 'Погодження ком. умов', 40, false),
    ('CONTRACT_APPROVAL', 'Погодження договору', 50, false),
    ('CONTRACT_SIGNED', 'Договір підписано', 60, false),
    ('OPENED', 'Відкрито', 70, true),
    ('PAUSED', 'На паузі', 80, false),
    ('REFUSED', 'Відмова', 90, true);

INSERT INTO object_types(code, name) VALUES
    ('LAND_PLOT', 'Земельна ділянка'),
    ('BUILT_IN_PREMISES', 'Вбудоване приміщення'),
    ('RESIDENTIAL_GROUND_FLOOR', 'Перший поверх житлового'),
    ('SHOPPING_CENTER', 'ТЦ');

INSERT INTO refusal_reasons(code, name) VALUES
    ('COMMERCIAL_TERMS', 'Комерційні умови'),
    ('CONTRACT_TERMS', 'Умови договору'),
    ('TECHNICAL_PARAMETERS', 'Тех. параметри'),
    ('LOW_POTENTIAL', 'Малий потенціал'),
    ('LOST_TO_COMPETITORS', 'Програли конкурентам'),
    ('IC_NOT_APPROVED', 'Не погоджено на ІК');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_FOUND_TO_IN_PROGRESS', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'FOUND' AND target.code = 'IN_PROGRESS';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_FOUND_TO_IN_PROGRESS' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_FOUND_TO_PAUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'FOUND' AND target.code = 'PAUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_FOUND_TO_PAUSED' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_FOUND_TO_REFUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'FOUND' AND target.code = 'REFUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_FOUND_TO_REFUSED' AND role.code IN ('MANAGER');

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('REFUSAL_REASON')) required(field_code)
WHERE transition.code = 'MANAGER_FOUND_TO_REFUSED';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_IN_PROGRESS_TO_FOUND', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IN_PROGRESS' AND target.code = 'FOUND';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_IN_PROGRESS_TO_FOUND' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_IN_PROGRESS_TO_PAUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IN_PROGRESS' AND target.code = 'PAUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_IN_PROGRESS_TO_PAUSED' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_IN_PROGRESS_TO_REFUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IN_PROGRESS' AND target.code = 'REFUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_IN_PROGRESS_TO_REFUSED' AND role.code IN ('MANAGER');

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('REFUSAL_REASON')) required(field_code)
WHERE transition.code = 'MANAGER_IN_PROGRESS_TO_REFUSED';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_PAUSED_TO_FOUND', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'PAUSED' AND target.code = 'FOUND';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_PAUSED_TO_FOUND' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_PAUSED_TO_IN_PROGRESS', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'PAUSED' AND target.code = 'IN_PROGRESS';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_PAUSED_TO_IN_PROGRESS' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_PAUSED_TO_REFUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'PAUSED' AND target.code = 'REFUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_PAUSED_TO_REFUSED' AND role.code IN ('MANAGER');

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('REFUSAL_REASON')) required(field_code)
WHERE transition.code = 'MANAGER_PAUSED_TO_REFUSED';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_REFUSED_TO_FOUND', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'REFUSED' AND target.code = 'FOUND';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_REFUSED_TO_FOUND' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_REFUSED_TO_IN_PROGRESS', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'REFUSED' AND target.code = 'IN_PROGRESS';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_REFUSED_TO_IN_PROGRESS' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'MANAGER_REFUSED_TO_PAUSED', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'REFUSED' AND target.code = 'PAUSED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'MANAGER_REFUSED_TO_PAUSED' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'LEAD_FOUND_TO_IC', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'FOUND' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'LEAD_FOUND_TO_IC' AND role.code IN ('REGIONAL_MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'REQUEST_FOUND_TO_IC', source.id, target.id, true, (SELECT id FROM roles WHERE code = 'REGIONAL_MANAGER'), false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'FOUND' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'REQUEST_FOUND_TO_IC' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'LEAD_IN_PROGRESS_TO_IC', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IN_PROGRESS' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'LEAD_IN_PROGRESS_TO_IC' AND role.code IN ('REGIONAL_MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'REQUEST_IN_PROGRESS_TO_IC', source.id, target.id, true, (SELECT id FROM roles WHERE code = 'REGIONAL_MANAGER'), false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IN_PROGRESS' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'REQUEST_IN_PROGRESS_TO_IC' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'LEAD_PAUSED_TO_IC', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'PAUSED' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'LEAD_PAUSED_TO_IC' AND role.code IN ('REGIONAL_MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'REQUEST_PAUSED_TO_IC', source.id, target.id, true, (SELECT id FROM roles WHERE code = 'REGIONAL_MANAGER'), false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'PAUSED' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'REQUEST_PAUSED_TO_IC' AND role.code IN ('MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'IC_APPROVED', source.id, target.id, false, NULL, true, false, 'APPROVED', true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IC_PREPARATION' AND target.code = 'COMMERCIAL_TERMS_APPROVAL';

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('INVESTMENT_COMMITTEE_DATE')) required(field_code)
WHERE transition.code = 'IC_APPROVED';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'IC_APPROVED_WITH_CONDITIONS', source.id, target.id, false, NULL, true, false, 'APPROVED_WITH_CONDITIONS', true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IC_PREPARATION' AND target.code = 'COMMERCIAL_TERMS_APPROVAL';

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('INVESTMENT_COMMITTEE_DATE'), ('INVESTMENT_COMMITTEE_CONDITIONS')) required(field_code)
WHERE transition.code = 'IC_APPROVED_WITH_CONDITIONS';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'IC_REFUSED', source.id, target.id, false, NULL, true, false, 'REFUSED', true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IC_PREPARATION' AND target.code = 'REFUSED';

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('INVESTMENT_COMMITTEE_DATE'), ('REFUSAL_REASON')) required(field_code)
WHERE transition.code = 'IC_REFUSED';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'REQUEST_CONTRACT_APPROVAL', source.id, target.id, true, (SELECT id FROM roles WHERE code = 'CONTRACT_MANAGER'), false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'COMMERCIAL_TERMS_APPROVAL' AND target.code = 'CONTRACT_APPROVAL';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'REQUEST_CONTRACT_APPROVAL' AND role.code IN ('REGIONAL_MANAGER');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'SIGN_CONTRACT', source.id, target.id, false, NULL, false, false, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'CONTRACT_APPROVAL' AND target.code = 'CONTRACT_SIGNED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'SIGN_CONTRACT' AND role.code IN ('REGIONAL_MANAGER', 'CONTRACT_MANAGER');

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('CONTRACT_SIGNED_DATE')) required(field_code)
WHERE transition.code = 'SIGN_CONTRACT';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'OPEN_OBJECT', source.id, target.id, false, NULL, false, false, NULL, false
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'CONTRACT_SIGNED' AND target.code = 'OPENED';

INSERT INTO object_transition_required_fields(transition_id, field_code)
SELECT transition.id, required.field_code FROM object_status_transitions transition
CROSS JOIN (VALUES ('OPENING_DATE')) required(field_code)
WHERE transition.code = 'OPEN_OBJECT';

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'RETURN_IC_PREPARATION', source.id, target.id, false, NULL, false, true, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'IC_PREPARATION' AND target.code = 'IN_PROGRESS';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'RETURN_IC_PREPARATION' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'RETURN_COMMERCIAL_TERMS_APPROVAL', source.id, target.id, false, NULL, false, true, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'COMMERCIAL_TERMS_APPROVAL' AND target.code = 'IC_PREPARATION';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'RETURN_COMMERCIAL_TERMS_APPROVAL' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'RETURN_CONTRACT_APPROVAL', source.id, target.id, false, NULL, false, true, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'CONTRACT_APPROVAL' AND target.code = 'COMMERCIAL_TERMS_APPROVAL';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'RETURN_CONTRACT_APPROVAL' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'RETURN_CONTRACT_SIGNED', source.id, target.id, false, NULL, false, true, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'CONTRACT_SIGNED' AND target.code = 'CONTRACT_APPROVAL';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'RETURN_CONTRACT_SIGNED' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR');

INSERT INTO object_status_transitions
    (code, from_status_id, to_status_id, is_confirmation_required, confirming_role_id,
     is_system_only, is_rollback, investment_committee_decision, is_active)
SELECT 'RETURN_OPENED', source.id, target.id, false, NULL, false, true, NULL, true
FROM object_statuses source CROSS JOIN object_statuses target
WHERE source.code = 'OPENED' AND target.code = 'CONTRACT_SIGNED';

INSERT INTO object_transition_roles(transition_id, role_id)
SELECT transition.id, role.id FROM object_status_transitions transition CROSS JOIN roles role
WHERE transition.code = 'RETURN_OPENED' AND role.code IN ('REGIONAL_MANAGER', 'DEVELOPMENT_DIRECTOR');
