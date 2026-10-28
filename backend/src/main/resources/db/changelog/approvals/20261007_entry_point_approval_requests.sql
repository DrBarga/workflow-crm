CREATE TABLE entry_point_approval_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE RESTRICT,
    initiator_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    confirming_role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    confirming_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    longitude numeric(10,7) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    latitude numeric(9,7) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    object_version bigint NOT NULL CHECK (object_version >= 0),
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','STALE')),
    decided_by_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_entry_point_confirmer CHECK
        ((confirming_role_id IS NULL) <> (confirming_user_id IS NULL))
);
CREATE UNIQUE INDEX ux_entry_point_approval_pending ON entry_point_approval_requests(object_id) WHERE status='PENDING';
CREATE INDEX ix_entry_point_approval_initiator ON entry_point_approval_requests(initiator_id);
CREATE INDEX ix_entry_point_approval_role ON entry_point_approval_requests(confirming_role_id,status);
CREATE INDEX ix_entry_point_approval_user ON entry_point_approval_requests(confirming_user_id,status);
CREATE INDEX ix_entry_point_approval_decider ON entry_point_approval_requests(decided_by_id);
