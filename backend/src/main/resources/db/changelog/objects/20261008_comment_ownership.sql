CREATE TABLE IF NOT EXISTS object_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body varchar(1000) NOT NULL CHECK (btrim(body) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX IF NOT EXISTS ix_object_comments_object ON object_comments(object_id,created_at,id);
CREATE INDEX IF NOT EXISTS ix_object_comments_author ON object_comments(author_id);
CREATE TABLE IF NOT EXISTS task_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body varchar(1000) NOT NULL CHECK (btrim(body) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX IF NOT EXISTS ix_task_comments_task ON task_comments(task_id,created_at,id);
CREATE INDEX IF NOT EXISTS ix_task_comments_author ON task_comments(author_id);
