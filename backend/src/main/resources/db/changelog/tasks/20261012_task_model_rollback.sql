DROP TABLE task_dependencies;
ALTER TABLE tasks DROP CONSTRAINT ux_tasks_id_object, DROP CONSTRAINT ck_tasks_completed_at,
    DROP CONSTRAINT ck_tasks_version_set, DROP CONSTRAINT ck_tasks_source_version,
    DROP CONSTRAINT fk_tasks_source_version, DROP CONSTRAINT fk_tasks_set_version, DROP CONSTRAINT fk_tasks_set,
    DROP COLUMN source_item_id, DROP COLUMN template_version_id, DROP COLUMN task_set_id, DROP COLUMN completed_at;
DROP TABLE task_sets;
DROP TABLE template_item_dependencies;
DROP TABLE template_items;
DROP TABLE task_template_versions;
ALTER TABLE task_templates DROP COLUMN contract_type_id;
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_status;
DROP INDEX ix_tasks_status;
DROP TABLE task_statuses;
