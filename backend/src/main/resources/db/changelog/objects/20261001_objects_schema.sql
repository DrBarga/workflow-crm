CREATE TABLE object_statuses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    sort_order integer NOT NULL UNIQUE CHECK (sort_order > 0),
    is_task_generation_trigger boolean NOT NULL DEFAULT false,
    is_final boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE object_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE refusal_reasons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE object_status_transitions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    from_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    to_status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    is_confirmation_required boolean NOT NULL DEFAULT false,
    confirming_role_id uuid REFERENCES roles(id) ON DELETE RESTRICT,
    is_system_only boolean NOT NULL DEFAULT false,
    is_rollback boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    investment_committee_decision varchar(32),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_object_transitions_different_statuses CHECK (from_status_id <> to_status_id),
    CONSTRAINT ck_object_transitions_confirmation CHECK
        (is_confirmation_required = (confirming_role_id IS NOT NULL)),
    CONSTRAINT ck_object_transitions_system_confirmation CHECK
        (NOT is_system_only OR NOT is_confirmation_required),
    CONSTRAINT ck_object_transitions_decision CHECK
        (investment_committee_decision IS NULL OR
         (is_system_only AND investment_committee_decision IN ('APPROVED', 'APPROVED_WITH_CONDITIONS', 'REFUSED')))
);
CREATE INDEX ix_object_transitions_from_status ON object_status_transitions(from_status_id);
CREATE INDEX ix_object_transitions_to_status ON object_status_transitions(to_status_id);
CREATE INDEX ix_object_transitions_confirming_role ON object_status_transitions(confirming_role_id);

CREATE TABLE object_transition_roles (
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transition_id, role_id)
);
CREATE INDEX ix_object_transition_roles_role ON object_transition_roles(role_id);

CREATE TABLE object_transition_required_fields (
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    field_code varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transition_id, field_code),
    CONSTRAINT ck_object_transition_required_field CHECK (field_code IN
        ('INVESTMENT_COMMITTEE_DATE', 'INVESTMENT_COMMITTEE_CONDITIONS', 'REFUSAL_REASON',
         'CONTRACT_SIGNED_DATE', 'OPENING_DATE'))
);

CREATE TABLE objects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(250) CHECK (btrim(name) <> ''),
    object_type_id uuid REFERENCES object_types(id) ON DELETE RESTRICT,
    address varchar(500) CHECK (btrim(address) <> ''),
    region_id uuid NOT NULL REFERENCES regions(id) ON DELETE RESTRICT,
    manager_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status_id uuid NOT NULL REFERENCES object_statuses(id) ON DELETE RESTRICT,
    status_before_pause_id uuid REFERENCES object_statuses(id) ON DELETE RESTRICT,
    entrance_longitude numeric(10,7) NOT NULL CHECK (entrance_longitude BETWEEN -180 AND 180),
    entrance_latitude numeric(9,7) NOT NULL CHECK (entrance_latitude BETWEEN -90 AND 90),
    object_area numeric(14,2) CHECK (object_area > 0),
    land_area numeric(14,2) CHECK (land_area > 0),
    contact_name varchar(200) NOT NULL CHECK (btrim(contact_name) <> ''),
    contact_role varchar(100) NOT NULL CHECK (btrim(contact_role) <> ''),
    contact_phone varchar(64),
    contact_email varchar(320),
    commercial_terms text,
    technical_specifications text,
    contract_type varchar(160),
    investment_committee_decision varchar(32),
    investment_committee_date date,
    investment_committee_conditions text,
    refusal_reason_id uuid REFERENCES refusal_reasons(id) ON DELETE RESTRICT,
    contract_signed_date date,
    opening_date date,
    author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_objects_area_required CHECK (object_area IS NOT NULL OR land_area IS NOT NULL),
    CONSTRAINT ck_objects_contact_channel CHECK
        (COALESCE(btrim(contact_phone), '') <> '' OR COALESCE(btrim(contact_email), '') <> ''),
    CONSTRAINT ck_objects_committee_decision CHECK (investment_committee_decision IS NULL OR
        investment_committee_decision IN ('APPROVED', 'APPROVED_WITH_CONDITIONS', 'REFUSED')),
    CONSTRAINT ck_objects_committee_date CHECK
        ((investment_committee_decision IS NULL) = (investment_committee_date IS NULL)),
    CONSTRAINT ck_objects_committee_conditions CHECK
        (investment_committee_decision IS DISTINCT FROM 'APPROVED_WITH_CONDITIONS' OR
         COALESCE(btrim(investment_committee_conditions), '') <> ''),
    CONSTRAINT ck_objects_committee_refusal_reason CHECK
        (investment_committee_decision IS DISTINCT FROM 'REFUSED' OR refusal_reason_id IS NOT NULL),
    CONSTRAINT ck_objects_opening_date CHECK
        (opening_date IS NULL OR (contract_signed_date IS NOT NULL AND opening_date >= contract_signed_date))
);
CREATE INDEX ix_objects_type ON objects(object_type_id);
CREATE INDEX ix_objects_region ON objects(region_id);
CREATE INDEX ix_objects_manager ON objects(manager_id);
CREATE INDEX ix_objects_status ON objects(status_id);
CREATE INDEX ix_objects_pause_status ON objects(status_before_pause_id);
CREATE INDEX ix_objects_refusal_reason ON objects(refusal_reason_id);
CREATE INDEX ix_objects_author ON objects(author_id);
CREATE INDEX ix_objects_created_at ON objects(created_at);

CREATE TABLE object_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL REFERENCES objects(id) ON DELETE RESTRICT,
    author_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    actor_type varchar(16) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    field_name varchar(100) NOT NULL CHECK (btrim(field_name) <> ''),
    previous_value text,
    new_value text,
    object_version bigint NOT NULL CHECK (object_version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_object_history_author CHECK ((actor_type = 'USER') = (author_id IS NOT NULL))
);
CREATE INDEX ix_object_history_object_created ON object_history(object_id, created_at);
CREATE INDEX ix_object_history_author ON object_history(author_id);
