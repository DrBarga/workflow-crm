DO $rollback$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id='20261027-ic-template-escalation' AND author='workflowcrm' AND exectype='EXECUTED'
    ) THEN RETURN;
    END IF;

    IF (SELECT count(*) FROM task_template_items
        WHERE template_id='58050000-0000-4000-8000-000000000000' AND escalation_days=1 AND version=1)<>4
    THEN
        RAISE EXCEPTION 'IC template was edited after seeding; refusing to overwrite it during rollback';
    END IF;

    UPDATE task_template_items SET escalation_days=NULL,version=0
    WHERE template_id='58050000-0000-4000-8000-000000000000';
END
$rollback$;
