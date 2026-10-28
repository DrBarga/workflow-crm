CREATE TABLE object_transition_validation_roles (
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (transition_id, role_id)
);
CREATE INDEX ix_object_transition_validation_role ON object_transition_validation_roles(role_id);

INSERT INTO object_transition_validation_roles(transition_id, role_id)
SELECT t.id, r.id FROM object_status_transitions t
JOIN object_statuses s ON s.id=t.from_status_id
JOIN roles r ON r.code='MANAGER'
WHERE t.is_active AND NOT t.is_system_only AND t.is_refusal_entry
  AND s.code IN ('IC_PREPARATION','COMMERCIAL_TERMS_APPROVAL','CONTRACT_APPROVAL','CONTRACT_SIGNED','OPENED');

CREATE VIEW object_refusal_tags AS
SELECT o.id AS object_id, r.id AS reason_id, r.code, r.name,
       o.region_id, o.manager_id, o.status_id, o.version
FROM objects o JOIN refusal_reasons r ON r.id=o.refusal_reason_id;
