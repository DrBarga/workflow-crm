ALTER TABLE task_template_items
    ALTER COLUMN assignee_id DROP NOT NULL,
    ADD COLUMN assignment_rule varchar(24) NOT NULL DEFAULT 'EXPLICIT_USER',
    ADD COLUMN role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_task_template_item_assignment CHECK (
        (assignment_rule='EXPLICIT_USER' AND assignee_id IS NOT NULL AND role_id IS NULL)
        OR (assignment_rule='OBJECT_MANAGER' AND assignee_id IS NULL AND role_id IS NULL)
        OR (assignment_rule IN ('REGIONAL_ROLE','NETWORK_ROLE') AND assignee_id IS NULL AND role_id IS NOT NULL)),
    ADD CONSTRAINT uq_task_template_item_template UNIQUE (id,template_id);
CREATE INDEX ix_task_template_item_role ON task_template_items(role_id);

CREATE TABLE task_template_item_dependencies (
    template_id uuid NOT NULL REFERENCES task_templates(id) ON DELETE CASCADE,
    item_id uuid NOT NULL,
    predecessor_id uuid NOT NULL,
    PRIMARY KEY (item_id,predecessor_id),
    CHECK (item_id<>predecessor_id),
    FOREIGN KEY (item_id,template_id) REFERENCES task_template_items(id,template_id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_id,template_id) REFERENCES task_template_items(id,template_id) ON DELETE CASCADE
);
CREATE INDEX ix_task_template_item_dependencies_template ON task_template_item_dependencies(template_id);
CREATE INDEX ix_task_template_item_dependencies_predecessor ON task_template_item_dependencies(predecessor_id);
