ALTER TABLE contacts ADD COLUMN deleted_at timestamptz;

CREATE TABLE contact_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id uuid NOT NULL REFERENCES contacts(id) ON DELETE RESTRICT,
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    contact_version bigint NOT NULL CHECK (contact_version >= 0),
    event_type varchar(16) NOT NULL CHECK (event_type IN ('CREATED', 'UPDATED', 'DELETED')),
    changed_fields jsonb NOT NULL CHECK (jsonb_typeof(changed_fields) = 'array'
        AND changed_fields <@ '["name","role","phone","email","comment","contactTypeId","objectIds","deletedAt"]'::jsonb),
    linked_object_ids jsonb NOT NULL CHECK (jsonb_typeof(linked_object_ids) = 'array'),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (contact_id, contact_version)
);
CREATE INDEX ix_contact_history_contact ON contact_history(contact_id, created_at);
CREATE INDEX ix_contact_history_actor ON contact_history(actor_id, created_at);
