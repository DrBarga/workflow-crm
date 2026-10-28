DO $body$
BEGIN
    IF EXISTS(SELECT 1 FROM objects WHERE preliminary_contract_signed_date IS NOT NULL
        OR commissioning_date IS NOT NULL OR short_comment IS NOT NULL) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: new object fields contain data';
    END IF;
    IF EXISTS(SELECT 1 FROM object_transition_required_tasks r JOIN object_status_transitions t ON t.id=r.transition_id
        WHERE t.code<>'SIGN_CONTRACT' OR r.item_code NOT IN
            ('APPROVE_LOTUS_CONTRACT','APPROVE_FINANCIAL_MODEL','APPROVE_QUESTIONNAIRE')) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: transition task requirements were configured';
    END IF;
    IF EXISTS(SELECT 1 FROM object_transition_required_fields WHERE field_code IN
        ('COMMISSIONING_DATE','CONSTRUCTION_ACCEPTANCE_PLANNED_DATE','CONSTRUCTION_TERM_DAYS','OPENING_PLANNED_DATE')
        AND transition_id NOT IN (SELECT transition_id FROM object_signing_policy_backup)) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: additional date requirements were configured';
    END IF;
    IF EXISTS(SELECT 1 FROM object_signing_policy_backup b JOIN object_status_transitions t ON t.id=b.transition_id
        WHERE t.version<>b.applied_version) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: signing policy changed';
    END IF;
    IF EXISTS(SELECT 1 FROM task_template_items WHERE code !~ '^ITEM_[A-F0-9]{32}$' AND code NOT IN
        ('APPROVE_LOTUS_CONTRACT','APPROVE_FINANCIAL_MODEL','APPROVE_QUESTIONNAIRE')) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: template item codes were configured';
    END IF;
    IF EXISTS(SELECT 1 FROM template_items WHERE code !~ '^ITEM_[A-F0-9]{32}$' AND code NOT IN
        ('APPROVE_LOTUS_CONTRACT','APPROVE_FINANCIAL_MODEL','APPROVE_QUESTIONNAIRE')) THEN
        RAISE EXCEPTION 'Cannot roll back object lifecycle: historical item codes contain configuration';
    END IF;
END;
$body$;
DROP TRIGGER tr_object_opening_plan ON objects;
DROP FUNCTION calculate_object_opening_plan();
UPDATE objects o SET opening_planned_date=b.previous_date,version=b.previous_version,updated_at=b.previous_updated_at
FROM object_opening_plan_backup b WHERE o.id=b.object_id AND o.version=b.applied_version
    AND o.construction_acceptance_planned_date IS NOT DISTINCT FROM b.acceptance_date
    AND o.construction_term_days IS NOT DISTINCT FROM b.term_days;
DROP TABLE object_opening_plan_backup;
ALTER TABLE objects DROP COLUMN preliminary_contract_signed_date,DROP COLUMN commissioning_date,DROP COLUMN short_comment;
UPDATE object_status_transitions t SET version=b.previous_version,updated_at=b.previous_updated_at
FROM object_signing_policy_backup b WHERE t.id=b.transition_id;
DELETE FROM object_transition_required_fields WHERE field_code IN
    ('COMMISSIONING_DATE','CONSTRUCTION_ACCEPTANCE_PLANNED_DATE','CONSTRUCTION_TERM_DAYS','OPENING_PLANNED_DATE');
ALTER TABLE object_transition_required_fields ALTER COLUMN field_code TYPE varchar(32);
DROP TABLE object_transition_required_tasks;
DROP TABLE object_signing_policy_backup;
ALTER TABLE object_transition_required_fields DROP CONSTRAINT ck_object_transition_required_field;
ALTER TABLE object_transition_required_fields ADD CONSTRAINT ck_object_transition_required_field CHECK
    (field_code IN ('INVESTMENT_COMMITTEE_DATE','INVESTMENT_COMMITTEE_CONDITIONS','REFUSAL_REASON',
        'CONTRACT_SIGNED_DATE','OPENING_DATE','CONTRACT_TYPE'));
ALTER TABLE task_template_items DROP COLUMN code;
ALTER TABLE template_items DROP COLUMN code;
