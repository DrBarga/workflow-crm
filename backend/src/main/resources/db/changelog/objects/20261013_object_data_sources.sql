CREATE TABLE object_data_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO object_data_sources(code, name) VALUES
    ('BROKER', 'Брокер'),
    ('OWN_SEARCH', 'Власний пошук'),
    ('ADVERTISEMENT', 'Оголошення'),
    ('DIRECT_OWNER', 'Власник напряму'),
    ('OTHER', 'Інше');
ALTER TABLE objects ADD COLUMN data_source_id uuid
    REFERENCES object_data_sources(id) ON DELETE RESTRICT;
CREATE INDEX ix_objects_data_source ON objects(data_source_id);
