LOCK TABLE task_template_items,template_items,tasks IN ACCESS EXCLUSIVE MODE;

ALTER TABLE tasks ADD COLUMN manually_paused boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_tasks_manual_pause CHECK(NOT manually_paused OR status='PAUSED');

DO $body$
BEGIN
    IF EXISTS (
        SELECT source_id FROM (
            SELECT id AS source_id,template_id,code FROM task_template_items
            UNION ALL
            SELECT i.source_item_id,v.template_id,i.code FROM template_items i
                JOIN task_template_versions v ON v.id=i.template_version_id
        ) definitions GROUP BY source_id HAVING count(DISTINCT (template_id,code))>1
    ) THEN
        RAISE EXCEPTION 'Template element codes differ between versions; reconcile existing definitions before migration';
    END IF;
END;
$body$;

ALTER TABLE task_template_items ALTER COLUMN code DROP DEFAULT;
ALTER TABLE template_items ALTER COLUMN code DROP DEFAULT;
CREATE INDEX ix_template_items_source_item ON template_items(source_item_id);

CREATE FUNCTION preserve_task_element_code() RETURNS trigger LANGUAGE plpgsql AS $body$
DECLARE
    source_id uuid;
    element_template_id uuid;
    existing_code varchar(100);
    different_template boolean;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF NEW.code IS DISTINCT FROM OLD.code
            OR (TG_TABLE_NAME='task_template_items'
                AND (to_jsonb(NEW)->>'id',to_jsonb(NEW)->>'template_id') IS DISTINCT FROM
                    (to_jsonb(OLD)->>'id',to_jsonb(OLD)->>'template_id'))
            OR (TG_TABLE_NAME='template_items'
                AND (to_jsonb(NEW)->>'source_item_id',to_jsonb(NEW)->>'template_version_id') IS DISTINCT FROM
                    (to_jsonb(OLD)->>'source_item_id',to_jsonb(OLD)->>'template_version_id')) THEN
            RAISE EXCEPTION 'Template element identity and code are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_TABLE_NAME='task_template_items' THEN
        source_id=NEW.id;
        element_template_id=NEW.template_id;
    ELSE
        source_id=NEW.source_item_id;
        SELECT v.template_id INTO element_template_id FROM task_template_versions v WHERE v.id=NEW.template_version_id;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(source_id::text,580404));
    SELECT min(d.code),bool_or(d.template_id IS DISTINCT FROM element_template_id)
        INTO existing_code,different_template
    FROM (
        SELECT i.template_id,i.code FROM task_template_items i WHERE i.id=source_id
        UNION ALL
        SELECT v.template_id,i.code FROM template_items i JOIN task_template_versions v ON v.id=i.template_version_id
            WHERE i.source_item_id=source_id
    ) d;
    IF different_template OR (existing_code IS NOT NULL AND NEW.code IS NOT NULL AND NEW.code<>existing_code) THEN
        RAISE EXCEPTION 'Template element code must match its existing versions' USING ERRCODE='23514';
    END IF;
    NEW.code=coalesce(NEW.code,existing_code,'ITEM_'||upper(replace(source_id::text,'-','')));
    RETURN NEW;
END;
$body$;

CREATE TRIGGER preserve_task_template_item_code BEFORE INSERT OR UPDATE OF id,template_id,code
    ON task_template_items FOR EACH ROW EXECUTE FUNCTION preserve_task_element_code();
CREATE TRIGGER preserve_template_item_code BEFORE INSERT OR UPDATE OF source_item_id,template_version_id,code
    ON template_items FOR EACH ROW EXECUTE FUNCTION preserve_task_element_code();
