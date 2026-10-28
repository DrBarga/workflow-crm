DELETE FROM object_types t WHERE t.code = 'HANGAR'
AND NOT EXISTS (SELECT 1 FROM object_type_revision_backups b WHERE b.id = t.id);
UPDATE object_types t SET name = b.name, is_active = b.is_active, version = b.version,
    created_at = b.created_at, updated_at = b.updated_at
FROM object_type_revision_backups b WHERE b.id = t.id;
DROP TABLE object_type_revision_backups;

ALTER TABLE object_statuses ADD COLUMN is_task_generation_trigger boolean NOT NULL DEFAULT false;
UPDATE object_statuses s SET is_task_generation_trigger = b.was_task_generation_trigger
FROM object_status_trigger_backups b WHERE b.status_id = s.id;
DROP TABLE object_status_trigger_backups;

ALTER TABLE objects DROP CONSTRAINT ck_objects_committee_conditions;
UPDATE objects SET legacy_investment_committee_conditions =
    COALESCE(investment_committee_conditions, legacy_investment_committee_conditions);
ALTER TABLE objects DROP COLUMN investment_committee_conditions;
ALTER TABLE objects DROP COLUMN investment_committee_refusal_comment;
ALTER TABLE objects RENAME COLUMN legacy_investment_committee_conditions TO investment_committee_conditions;
ALTER TABLE objects ADD CONSTRAINT ck_objects_committee_conditions CHECK
    (investment_committee_decision IS DISTINCT FROM 'APPROVED_WITH_CONDITIONS'
     OR COALESCE(btrim(investment_committee_conditions), '') <> '');

ALTER TABLE objects DROP COLUMN building_area;
ALTER TABLE objects DROP COLUMN first_floor_area;
ALTER TABLE objects DROP COLUMN second_floor_area;
ALTER TABLE objects DROP COLUMN basement_area;
ALTER TABLE objects DROP COLUMN sales_hall_area;
ALTER TABLE objects DROP COLUMN floor_slab_height;
ALTER TABLE objects DROP COLUMN connected_power;
ALTER TABLE objects DROP COLUMN water_supply_flow;
ALTER TABLE objects DROP COLUMN fixed_rent_amount;
ALTER TABLE objects DROP COLUMN rent_indexation_percent;
ALTER TABLE objects DROP COLUMN turnover_payment_percent;
ALTER TABLE objects DROP COLUMN contract_duration_years;
ALTER TABLE objects DROP COLUMN lease_terms;
ALTER TABLE objects DROP COLUMN power_connection_category;
ALTER TABLE objects DROP COLUMN sewage_type;
ALTER TABLE objects DROP COLUMN counterparty_tax_status;
ALTER TABLE objects DROP COLUMN construction_term_days;
ALTER TABLE objects DROP COLUMN construction_acceptance_planned_date;
ALTER TABLE objects DROP COLUMN construction_acceptance_actual_date;
ALTER TABLE objects DROP COLUMN opening_planned_date;
ALTER TABLE objects DROP COLUMN payment_act_planned_date;
ALTER TABLE objects DROP COLUMN payment_act_actual_date;
ALTER TABLE objects DROP COLUMN contract_type_id;
DROP TABLE contract_types;
