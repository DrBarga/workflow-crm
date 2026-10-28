CREATE TABLE contact_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

INSERT INTO contact_types(code, name) VALUES
    ('OWNER', 'Власник'),
    ('LAWYER', 'Юрист'),
    ('ENGINEER', 'Інженер'),
    ('OPERATIONS', 'Експлуатація');

ALTER TABLE contacts
    ADD COLUMN comment varchar(2000),
    ADD COLUMN author_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN contact_type_id uuid REFERENCES contact_types(id) ON DELETE RESTRICT,
    ADD COLUMN normalized_phone varchar(64) GENERATED ALWAYS AS
        (NULLIF(regexp_replace(regexp_replace(phone, '[^0-9]', '', 'g'), '^00', ''), '')) STORED;

CREATE INDEX ix_contacts_author ON contacts(author_id);
CREATE INDEX ix_contacts_type ON contacts(contact_type_id);
CREATE INDEX ix_contacts_normalized_phone ON contacts(normalized_phone text_pattern_ops);
CREATE INDEX ix_contacts_name ON contacts(lower(name) text_pattern_ops);

CREATE TABLE contact_phone_views (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id uuid NOT NULL REFERENCES contacts(id) ON DELETE RESTRICT,
    viewer_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    viewed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE INDEX ix_contact_phone_views_contact ON contact_phone_views(contact_id, viewed_at);
CREATE INDEX ix_contact_phone_views_viewer ON contact_phone_views(viewer_id, viewed_at);
