CREATE TABLE task_statuses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(16) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK(length(btrim(name))>0),
    sort_order integer NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO task_statuses(code,name,sort_order) VALUES
('NEW','Не розпочата',0),('IN_PROGRESS','У роботі',1),('PAUSED','На паузі',2),
('WAITING_APPROVAL','На погодженні',3),('DONE','Виконана',4),('CANCELLED','Скасована',5);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_status FOREIGN KEY(status) REFERENCES task_statuses(code) ON DELETE RESTRICT;
CREATE INDEX ix_tasks_status ON tasks(status);

CREATE TABLE task_sets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    template_version_id uuid,
    trigger_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    state varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK(state IN ('ACTIVE','COMPLETED','CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    UNIQUE(id,object_id,trigger_status_id), UNIQUE(id,template_version_id),
    FOREIGN KEY(template_version_id,trigger_status_id)
        REFERENCES task_template_versions(id,trigger_status_id) ON DELETE RESTRICT
);
CREATE INDEX ix_task_sets_object ON task_sets(object_id);
CREATE INDEX ix_task_sets_template_version ON task_sets(template_version_id);
CREATE INDEX ix_task_sets_trigger ON task_sets(trigger_status_id);

ALTER TABLE tasks ADD COLUMN completed_at timestamptz,
    ADD COLUMN task_set_id uuid,
    ADD COLUMN template_version_id uuid REFERENCES task_template_versions(id) ON DELETE RESTRICT,
    ADD COLUMN source_item_id uuid,
    ADD CONSTRAINT fk_tasks_set FOREIGN KEY(task_set_id,object_id,source_status_id)
        REFERENCES task_sets(id,object_id,trigger_status_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_tasks_set_version FOREIGN KEY(task_set_id,template_version_id)
        REFERENCES task_sets(id,template_version_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_tasks_source_version FOREIGN KEY(source_item_id,template_version_id)
        REFERENCES template_items(id,template_version_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_tasks_source_version CHECK((source_item_id IS NULL)=(template_version_id IS NULL)),
    ADD CONSTRAINT ck_tasks_version_set CHECK(template_version_id IS NULL OR task_set_id IS NOT NULL),
    ADD CONSTRAINT ck_tasks_completed_at CHECK(completed_at IS NULL OR status='DONE'),
    ADD CONSTRAINT ux_tasks_id_object UNIQUE(id,object_id);
CREATE INDEX ix_tasks_set ON tasks(task_set_id);
CREATE INDEX ix_tasks_template_version ON tasks(template_version_id);
CREATE INDEX ix_tasks_source_item ON tasks(source_item_id);

CREATE TABLE task_dependencies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    task_id uuid NOT NULL,
    predecessor_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(task_id<>predecessor_id), UNIQUE(task_id,predecessor_id),
    FOREIGN KEY(task_id,object_id) REFERENCES tasks(id,object_id) ON DELETE CASCADE,
    FOREIGN KEY(predecessor_id,object_id) REFERENCES tasks(id,object_id) ON DELETE CASCADE
);
CREATE INDEX ix_task_dependencies_object ON task_dependencies(object_id);
CREATE INDEX ix_task_dependencies_predecessor ON task_dependencies(predecessor_id);
