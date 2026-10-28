CREATE TABLE object_duplicate_decisions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    object_id uuid REFERENCES objects(id) ON DELETE RESTRICT,
    decision varchar(16) NOT NULL CHECK (decision IN ('CREATED', 'CANCELLED')),
    match_count bigint NOT NULL CHECK (match_count >= 0),
    nearest_object_ids uuid[] NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (actor_id, request_key),
    CHECK ((decision = 'CREATED') = (object_id IS NOT NULL)),
    CHECK (decision <> 'CREATED' OR match_count > 0),
    CHECK (cardinality(nearest_object_ids) <= 20)
);
CREATE INDEX ix_object_duplicate_decisions_object ON object_duplicate_decisions(object_id);
