CREATE TABLE contract_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO contract_types(code, name) VALUES ('PRIMARY', 'Основний'), ('PRELIMINARY', 'Попередній');

ALTER TABLE objects ADD COLUMN contract_type_id uuid REFERENCES contract_types(id) ON DELETE RESTRICT;
CREATE INDEX ix_objects_contract_type ON objects(contract_type_id);
UPDATE objects o SET contract_type_id = t.id FROM contract_types t
WHERE lower(btrim(o.contract_type)) IN (lower(t.code), lower(t.name));
ALTER TABLE objects ADD COLUMN building_area numeric(14,2) CHECK (building_area >= 0);
ALTER TABLE objects ADD COLUMN first_floor_area numeric(14,2) CHECK (first_floor_area >= 0);
ALTER TABLE objects ADD COLUMN second_floor_area numeric(14,2) CHECK (second_floor_area >= 0);
ALTER TABLE objects ADD COLUMN basement_area numeric(14,2) CHECK (basement_area >= 0);
ALTER TABLE objects ADD COLUMN sales_hall_area numeric(14,2) CHECK (sales_hall_area >= 0);
ALTER TABLE objects ADD COLUMN floor_slab_height numeric(14,2) CHECK (floor_slab_height >= 0);
ALTER TABLE objects ADD COLUMN connected_power numeric(14,2) CHECK (connected_power >= 0);
ALTER TABLE objects ADD COLUMN water_supply_flow numeric(14,2) CHECK (water_supply_flow >= 0);
ALTER TABLE objects ADD COLUMN fixed_rent_amount numeric(14,2) CHECK (fixed_rent_amount >= 0);
ALTER TABLE objects ADD COLUMN rent_indexation_percent numeric(7,2) CHECK (rent_indexation_percent >= 0);
ALTER TABLE objects ADD COLUMN turnover_payment_percent numeric(5,2) CHECK (turnover_payment_percent >= 0);
ALTER TABLE objects ADD COLUMN contract_duration_years numeric(5,2) CHECK (contract_duration_years >= 0);
ALTER TABLE objects ADD COLUMN lease_terms text;
ALTER TABLE objects ADD COLUMN power_connection_category varchar(3)
    CHECK (power_connection_category IN ('I', 'II', 'III'));
ALTER TABLE objects ADD COLUMN sewage_type varchar(32)
    CHECK (sewage_type IN ('CENTRALIZED', 'LOCAL_TREATMENT', 'CESSPOOL'));
ALTER TABLE objects ADD COLUMN counterparty_tax_status varchar(40)
    CHECK (counterparty_tax_status IN ('SOLE_PROPRIETOR_GROUP_3_NON_VAT', 'SOLE_PROPRIETOR_GROUP_3_VAT',
        'COMPANY_NON_VAT', 'COMPANY_VAT', 'INDIVIDUAL'));
ALTER TABLE objects ADD COLUMN construction_term_days integer CHECK (construction_term_days >= 0);
ALTER TABLE objects ADD CONSTRAINT ck_objects_turnover_percent CHECK (turnover_payment_percent <= 100);
ALTER TABLE objects ADD COLUMN construction_acceptance_planned_date date;
ALTER TABLE objects ADD COLUMN construction_acceptance_actual_date date;
ALTER TABLE objects ADD COLUMN opening_planned_date date;
ALTER TABLE objects ADD COLUMN payment_act_planned_date date;
ALTER TABLE objects ADD COLUMN payment_act_actual_date date;

ALTER TABLE objects DROP CONSTRAINT ck_objects_committee_conditions;
ALTER TABLE objects RENAME COLUMN investment_committee_conditions TO legacy_investment_committee_conditions;
ALTER TABLE objects ADD COLUMN investment_committee_conditions varchar(50);
ALTER TABLE objects ADD COLUMN investment_committee_refusal_comment varchar(50);
UPDATE objects SET investment_committee_conditions = legacy_investment_committee_conditions
WHERE char_length(legacy_investment_committee_conditions) <= 50;
ALTER TABLE objects ADD CONSTRAINT ck_objects_committee_conditions CHECK
    (investment_committee_decision IS DISTINCT FROM 'APPROVED_WITH_CONDITIONS'
     OR COALESCE(NULLIF(btrim(investment_committee_conditions), ''),
                 NULLIF(btrim(legacy_investment_committee_conditions), ''), '') <> '');

CREATE TABLE object_status_trigger_backups (
    status_id uuid PRIMARY KEY REFERENCES object_statuses(id) ON DELETE CASCADE,
    was_task_generation_trigger boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO object_status_trigger_backups(status_id, was_task_generation_trigger)
SELECT id, is_task_generation_trigger FROM object_statuses;
ALTER TABLE object_statuses DROP COLUMN is_task_generation_trigger;

CREATE TABLE object_type_revision_backups (LIKE object_types INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE object_type_revision_backups ADD PRIMARY KEY (id);
INSERT INTO object_type_revision_backups SELECT * FROM object_types;
UPDATE object_types SET is_active = false, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code = 'RESIDENTIAL_GROUND_FLOOR';
INSERT INTO object_types(code, name) VALUES ('HANGAR', 'Ангар') ON CONFLICT (code) DO NOTHING;
