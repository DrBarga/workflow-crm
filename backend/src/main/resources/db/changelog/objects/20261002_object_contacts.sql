CREATE TABLE contacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    role varchar(100) NOT NULL CHECK (btrim(role) <> ''),
    phone varchar(64),
    email varchar(320),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_contacts_channel CHECK (COALESCE(btrim(phone), '') <> '' OR COALESCE(btrim(email), '') <> '')
);
CREATE TABLE object_contacts (
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE CASCADE,
    contact_id uuid NOT NULL REFERENCES contacts(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (object_id, contact_id)
);
CREATE INDEX ix_object_contacts_contact ON object_contacts(contact_id);
ALTER TABLE object_creation_requests ADD COLUMN contact_id uuid REFERENCES contacts(id) ON DELETE RESTRICT;
CREATE INDEX ix_object_creation_requests_contact ON object_creation_requests(contact_id);

WITH legacy_contacts AS (
    SELECT id AS object_id, gen_random_uuid() AS contact_id, contact_name, contact_role, contact_phone, contact_email,
           created_at, updated_at FROM objects WHERE contact_name IS NOT NULL AND contact_role IS NOT NULL
), inserted_contacts AS (
    INSERT INTO contacts(id,name,role,phone,email,created_at,updated_at)
    SELECT contact_id,contact_name,contact_role,contact_phone,contact_email,created_at,updated_at FROM legacy_contacts
    RETURNING id
)
INSERT INTO object_contacts(object_id,contact_id)
SELECT legacy.object_id,legacy.contact_id FROM legacy_contacts legacy JOIN inserted_contacts saved ON saved.id=legacy.contact_id;

UPDATE object_creation_requests request SET contact_id=link.contact_id
FROM object_contacts link WHERE request.object_id=link.object_id;
