DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM task_template_items WHERE assignment_rule<>'EXPLICIT_USER')
       OR EXISTS (SELECT 1 FROM task_template_item_dependencies) THEN
        RAISE EXCEPTION 'Task preview rollback requires explicit assignees and no draft dependencies';
    END IF;
END $$;
DROP TABLE task_template_item_dependencies;
ALTER TABLE task_template_items
    DROP CONSTRAINT uq_task_template_item_template,
    DROP CONSTRAINT ck_task_template_item_assignment,
    DROP COLUMN role_id,
    DROP COLUMN assignment_rule,
    ALTER COLUMN assignee_id SET NOT NULL;
