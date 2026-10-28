ALTER TABLE task_templates ADD COLUMN contract_type_id uuid REFERENCES contract_types(id) ON DELETE RESTRICT;
UPDATE task_templates t SET contract_type_id=c.id FROM contract_types c
WHERE t.contract_type=c.code OR t.contract_type=c.name;
CREATE INDEX ix_task_templates_contract_type ON task_templates(contract_type_id);
CREATE UNIQUE INDEX ux_task_templates_contract_pair ON task_templates(trigger_status_id,contract_type_id)
WHERE contract_type_id IS NOT NULL AND contract_type IS NULL;

CREATE TABLE task_template_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id uuid NOT NULL REFERENCES task_templates(id) ON DELETE CASCADE,
    revision integer NOT NULL CHECK(revision>0),
    fingerprint varchar(64) NOT NULL,
    trigger_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    contract_type_id uuid REFERENCES contract_types(id) ON DELETE RESTRICT,
    legacy_contract_type varchar(160),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    UNIQUE(template_id,revision), UNIQUE(template_id,fingerprint), UNIQUE(id,trigger_status_id)
);
CREATE INDEX ix_task_template_versions_status ON task_template_versions(trigger_status_id);
CREATE INDEX ix_task_template_versions_contract ON task_template_versions(contract_type_id);

CREATE TABLE template_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_version_id uuid NOT NULL REFERENCES task_template_versions(id) ON DELETE CASCADE,
    source_item_id uuid NOT NULL,
    name varchar(250) NOT NULL CHECK(length(btrim(name))>0),
    deadline_rule varchar(24) NOT NULL CHECK(deadline_rule IN ('FROM_START_CALENDAR','FROM_START_BUSINESS')),
    deadline_offset_days integer NOT NULL CHECK(deadline_offset_days BETWEEN 0 AND 3650),
    assignment_rule varchar(24) NOT NULL CHECK(assignment_rule IN ('EXPLICIT_USER','OBJECT_MANAGER','REGIONAL_ROLE','NETWORK_ROLE')),
    assignee_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    sort_order integer NOT NULL CHECK(sort_order>=0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    CHECK((assignment_rule='EXPLICIT_USER' AND assignee_id IS NOT NULL AND role_id IS NULL)
       OR (assignment_rule='OBJECT_MANAGER' AND assignee_id IS NULL AND role_id IS NULL)
       OR (assignment_rule IN ('REGIONAL_ROLE','NETWORK_ROLE') AND assignee_id IS NULL AND role_id IS NOT NULL)),
    UNIQUE(template_version_id,sort_order), UNIQUE(template_version_id,source_item_id),
    UNIQUE(id,template_version_id)
);
CREATE INDEX ix_template_items_assignee ON template_items(assignee_id);
CREATE INDEX ix_template_items_role ON template_items(role_id);

CREATE TABLE template_item_dependencies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_version_id uuid NOT NULL REFERENCES task_template_versions(id) ON DELETE CASCADE,
    item_id uuid NOT NULL,
    predecessor_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(item_id<>predecessor_id), UNIQUE(item_id,predecessor_id),
    FOREIGN KEY(item_id,template_version_id) REFERENCES template_items(id,template_version_id) ON DELETE CASCADE,
    FOREIGN KEY(predecessor_id,template_version_id) REFERENCES template_items(id,template_version_id) ON DELETE CASCADE
);
CREATE INDEX ix_template_item_dependencies_version ON template_item_dependencies(template_version_id);
CREATE INDEX ix_template_item_dependencies_predecessor ON template_item_dependencies(predecessor_id);
