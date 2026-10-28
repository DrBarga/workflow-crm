CREATE TABLE file_categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    is_restricted boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO file_categories(code, name, is_restricted) VALUES
    ('MEDIA', 'Медіа', false),
    ('TECHNICAL_DOCUMENTATION', 'Технічна документація', false),
    ('TITLE_DOCUMENTS', 'Правовстановлюючі', false),
    ('CONTRACTS', 'Договори', true),
    ('OTHER', 'Інше', false);
