ALTER TABLE objects
    ADD COLUMN preliminary_contract_signed_date date,
    ADD COLUMN commissioning_date date,
    ADD COLUMN short_comment varchar(200);

CREATE TABLE object_opening_plan_backup (
    object_id uuid PRIMARY KEY REFERENCES objects(id) ON DELETE CASCADE,
    previous_date date,
    acceptance_date date,
    term_days integer,
    previous_version bigint NOT NULL,
    previous_updated_at timestamptz NOT NULL,
    applied_version bigint NOT NULL
);
INSERT INTO object_opening_plan_backup
SELECT id,opening_planned_date,construction_acceptance_planned_date,construction_term_days,version,updated_at,
    version+CASE WHEN opening_planned_date IS DISTINCT FROM
        construction_acceptance_planned_date+construction_term_days THEN 1 ELSE 0 END
FROM objects;
UPDATE objects SET opening_planned_date=construction_acceptance_planned_date+construction_term_days,
    version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE opening_planned_date IS DISTINCT FROM construction_acceptance_planned_date+construction_term_days;

CREATE FUNCTION calculate_object_opening_plan() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    NEW.opening_planned_date := NEW.construction_acceptance_planned_date + NEW.construction_term_days;
    RETURN NEW;
END;
$body$;
CREATE TRIGGER tr_object_opening_plan BEFORE INSERT OR UPDATE ON objects
FOR EACH ROW EXECUTE FUNCTION calculate_object_opening_plan();

ALTER TABLE task_template_items ADD COLUMN code varchar(100);
UPDATE task_template_items SET code='ITEM_'||upper(replace(id::text,'-',''));
ALTER TABLE template_items ADD COLUMN code varchar(100);
UPDATE template_items SET code='ITEM_'||upper(replace(source_item_id::text,'-',''));

WITH named AS (
    SELECT i.id,n.code,row_number() OVER(PARTITION BY i.template_id,n.code ORDER BY i.sort_order,i.id) AS position
    FROM task_template_items i JOIN (VALUES
        ('APPROVE_LOTUS_CONTRACT','Погодити договір в системі Lotus'),
        ('APPROVE_FINANCIAL_MODEL','Погодити фінансову модель'),
        ('APPROVE_QUESTIONNAIRE','Погодити опитувальний лист')
    ) n(code,name) ON lower(btrim(i.name))=lower(n.name)
)
UPDATE task_template_items i SET code=n.code FROM named n WHERE i.id=n.id AND n.position=1;
ALTER TABLE task_template_items ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN code SET DEFAULT ('ITEM_'||upper(replace(gen_random_uuid()::text,'-',''))),
    ADD CONSTRAINT ck_task_template_items_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,99}$'),
    ADD CONSTRAINT uq_task_template_items_code UNIQUE(template_id,code);

WITH named AS (
    SELECT i.id,n.code,row_number() OVER(PARTITION BY i.template_version_id,n.code ORDER BY i.sort_order,i.id) AS position
    FROM template_items i JOIN (VALUES
        ('APPROVE_LOTUS_CONTRACT','Погодити договір в системі Lotus'),
        ('APPROVE_FINANCIAL_MODEL','Погодити фінансову модель'),
        ('APPROVE_QUESTIONNAIRE','Погодити опитувальний лист')
    ) n(code,name) ON lower(btrim(i.name))=lower(n.name)
)
UPDATE template_items i SET code=n.code FROM named n WHERE i.id=n.id AND n.position=1;
ALTER TABLE template_items ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN code SET DEFAULT ('ITEM_'||upper(replace(gen_random_uuid()::text,'-',''))),
    ADD CONSTRAINT ck_template_items_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,99}$'),
    ADD CONSTRAINT uq_template_items_code UNIQUE(template_version_id,code);

ALTER TABLE object_transition_required_fields ALTER COLUMN field_code TYPE varchar(64);
ALTER TABLE object_transition_required_fields DROP CONSTRAINT ck_object_transition_required_field;
ALTER TABLE object_transition_required_fields ADD CONSTRAINT ck_object_transition_required_field CHECK
    (field_code IN ('INVESTMENT_COMMITTEE_DATE','INVESTMENT_COMMITTEE_CONDITIONS','REFUSAL_REASON',
        'CONTRACT_SIGNED_DATE','OPENING_DATE','CONTRACT_TYPE','COMMISSIONING_DATE',
        'CONSTRUCTION_ACCEPTANCE_PLANNED_DATE','CONSTRUCTION_TERM_DAYS','OPENING_PLANNED_DATE'));
CREATE TABLE object_transition_required_tasks (
    transition_id uuid NOT NULL REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    item_code varchar(100) NOT NULL CHECK (item_code ~ '^[A-Z][A-Z0-9_]{0,99}$'),
    PRIMARY KEY(transition_id,item_code)
);
INSERT INTO object_transition_required_fields(transition_id,field_code)
SELECT t.id,f.code FROM object_status_transitions t CROSS JOIN (VALUES
    ('COMMISSIONING_DATE'),('CONSTRUCTION_ACCEPTANCE_PLANNED_DATE'),('CONSTRUCTION_TERM_DAYS'),('OPENING_PLANNED_DATE')
) f(code) WHERE t.code='SIGN_CONTRACT' ON CONFLICT DO NOTHING;
INSERT INTO object_transition_required_tasks(transition_id,item_code)
SELECT t.id,c.code FROM object_status_transitions t CROSS JOIN (VALUES
    ('APPROVE_LOTUS_CONTRACT'),('APPROVE_FINANCIAL_MODEL'),('APPROVE_QUESTIONNAIRE')
) c(code) WHERE t.code='SIGN_CONTRACT' ON CONFLICT DO NOTHING;
CREATE TABLE object_signing_policy_backup (
    transition_id uuid PRIMARY KEY REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    previous_version bigint NOT NULL,
    previous_updated_at timestamptz NOT NULL,
    applied_version bigint NOT NULL
);
INSERT INTO object_signing_policy_backup SELECT id,version,updated_at,version+1
FROM object_status_transitions WHERE code='SIGN_CONTRACT';
UPDATE object_status_transitions SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE code='SIGN_CONTRACT';
