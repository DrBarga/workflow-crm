ALTER TABLE objects ALTER COLUMN contact_name DROP NOT NULL;
ALTER TABLE objects ALTER COLUMN contact_role DROP NOT NULL;
ALTER TABLE objects DROP CONSTRAINT ck_objects_contact_channel;
ALTER TABLE objects ADD CONSTRAINT ck_objects_contact_channel CHECK (
    (contact_name IS NULL AND contact_role IS NULL AND contact_phone IS NULL AND contact_email IS NULL)
    OR (contact_name IS NOT NULL AND contact_role IS NOT NULL
        AND (COALESCE(btrim(contact_phone), '') <> '' OR COALESCE(btrim(contact_email), '') <> '')));
CREATE TABLE object_creation_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_key varchar(128) NOT NULL CHECK (btrim(request_key) <> ''),
    request_hash char(64) NOT NULL,
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_object_creation_requests_actor_key UNIQUE (actor_id, request_key)
);
CREATE INDEX ix_object_creation_requests_object ON object_creation_requests(object_id);
