CREATE TABLE task_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trigger_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    contract_type varchar(160),
    is_active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE NULLS NOT DISTINCT (trigger_status_id, contract_type)
);
CREATE TABLE task_template_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id uuid NOT NULL REFERENCES task_templates(id) ON DELETE CASCADE,
    name varchar(250) NOT NULL CHECK (length(btrim(name)) > 0),
    assignee_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    deadline_offset_days integer NOT NULL CHECK (deadline_offset_days BETWEEN 0 AND 3650),
    sort_order integer NOT NULL CHECK (sort_order >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (template_id, sort_order)
);
CREATE INDEX idx_task_template_items_assignee ON task_template_items(assignee_id);
CREATE TABLE tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    source_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    template_item_id uuid REFERENCES task_template_items(id) ON DELETE SET NULL,
    name varchar(250) NOT NULL CHECK (length(btrim(name)) > 0),
    assignee_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    deadline date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'PAUSED', 'WAITING_APPROVAL', 'DONE', 'CANCELLED')),
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tasks_object_stage_status ON tasks(object_id, source_status_id, status);
CREATE INDEX idx_tasks_template_item ON tasks(template_item_id);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX idx_tasks_author ON tasks(author_id);
CREATE INDEX idx_tasks_source_status ON tasks(source_status_id);
CREATE TABLE task_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_type varchar(16) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    previous_status varchar(16) NOT NULL,
    new_status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_task_history_task ON task_history(task_id);
CREATE INDEX idx_task_history_actor ON task_history(actor_id);
