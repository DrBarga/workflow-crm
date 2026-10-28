CREATE TABLE IF NOT EXISTS files (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid REFERENCES objects(id) ON DELETE CASCADE,
    task_id uuid REFERENCES tasks(id) ON DELETE CASCADE,
    category_id uuid REFERENCES file_categories(id) ON DELETE RESTRICT,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    bucket varchar(63) NOT NULL CHECK (btrim(bucket) <> ''),
    storage_key varchar(1024) NOT NULL CHECK (btrim(storage_key) <> ''),
    original_name varchar(255) NOT NULL CHECK (btrim(original_name) <> ''),
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 0 AND 20971520),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (num_nonnulls(object_id,task_id)=1),
    UNIQUE (bucket,storage_key)
);
CREATE INDEX IF NOT EXISTS ix_files_object ON files(object_id) WHERE object_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_files_task ON files(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_files_author ON files(author_id);
CREATE INDEX IF NOT EXISTS ix_files_category ON files(category_id);
