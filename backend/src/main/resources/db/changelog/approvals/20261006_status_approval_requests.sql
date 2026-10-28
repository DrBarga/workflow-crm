CREATE TABLE status_approval_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE RESTRICT,
    initiator_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    confirming_role_id uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE RESTRICT,
    object_version bigint NOT NULL CHECK (object_version >= 0),
    command text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','STALE')),
    decided_by_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_status_approval_pending ON status_approval_requests(object_id) WHERE status='PENDING';
CREATE INDEX ix_status_approval_initiator ON status_approval_requests(initiator_id);
CREATE INDEX ix_status_approval_role ON status_approval_requests(confirming_role_id,status);
CREATE INDEX ix_status_approval_transition ON status_approval_requests(transition_id);
CREATE INDEX ix_status_approval_decider ON status_approval_requests(decided_by_id);
