CREATE UNIQUE INDEX ux_task_sets_active_trigger ON task_sets(object_id,trigger_status_id) WHERE state='ACTIVE';

CREATE TABLE object_task_set_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    confirmation_token varchar(64) NOT NULL CHECK(confirmation_token ~ '^[a-f0-9]{64}$'),
    command jsonb NOT NULL,
    state varchar(24) NOT NULL DEFAULT 'QUEUED' CHECK(state IN ('QUEUED','SUCCEEDED','AWAITING_APPROVAL','FAILED')),
    task_set_id uuid REFERENCES task_sets(id) ON DELETE SET NULL,
    approval_request_id uuid REFERENCES status_approval_requests(id) ON DELETE SET NULL,
    error varchar(64),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
    available_at timestamptz NOT NULL DEFAULT current_timestamp,
    dispatched_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE UNIQUE INDEX ux_object_task_set_jobs_confirmation
    ON object_task_set_jobs(actor_id,object_id,confirmation_token) WHERE state<>'FAILED';
CREATE UNIQUE INDEX ux_object_task_set_jobs_pending ON object_task_set_jobs(object_id) WHERE state='QUEUED';
CREATE INDEX ix_object_task_set_jobs_ready ON object_task_set_jobs(available_at,created_at,id) WHERE state='QUEUED';
CREATE INDEX ix_object_task_set_jobs_set ON object_task_set_jobs(task_set_id);
CREATE INDEX ix_object_task_set_jobs_approval ON object_task_set_jobs(approval_request_id);
CREATE TABLE object_task_set_keys (
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    idempotency_key varchar(128) NOT NULL,
    fingerprint varchar(64) NOT NULL,
    job_id uuid NOT NULL REFERENCES object_task_set_jobs(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY(actor_id,idempotency_key)
);
CREATE INDEX ix_object_task_set_keys_job ON object_task_set_keys(job_id);
