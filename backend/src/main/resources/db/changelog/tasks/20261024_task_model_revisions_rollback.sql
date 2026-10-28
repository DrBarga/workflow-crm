LOCK TABLE task_template_items,template_items,tasks IN ACCESS EXCLUSIVE MODE;

DO $body$
BEGIN
    IF EXISTS(SELECT 1 FROM tasks WHERE manually_paused) THEN
        RAISE EXCEPTION 'Cannot roll back task model: manually paused tasks still exist';
    END IF;
END;
$body$;
DROP TRIGGER preserve_template_item_code ON template_items;
DROP TRIGGER preserve_task_template_item_code ON task_template_items;
DROP FUNCTION preserve_task_element_code();
DROP INDEX ix_template_items_source_item;
ALTER TABLE template_items ALTER COLUMN code SET DEFAULT ('ITEM_'||upper(replace(gen_random_uuid()::text,'-','')));
ALTER TABLE task_template_items ALTER COLUMN code SET DEFAULT ('ITEM_'||upper(replace(gen_random_uuid()::text,'-','')));
ALTER TABLE tasks DROP CONSTRAINT ck_tasks_manual_pause,DROP COLUMN manually_paused;
