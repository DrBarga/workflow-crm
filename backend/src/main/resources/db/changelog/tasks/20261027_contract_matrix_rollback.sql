DO $rollback$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM databasechangelog
        WHERE id='20261027-contract-template-matrix' AND author='workflowcrm' AND exectype='EXECUTED'
    ) THEN RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM task_template_versions WHERE template_id='58050000-0000-4000-8000-000000000010'
    ) OR EXISTS (
        SELECT 1 FROM task_templates WHERE id='58050000-0000-4000-8000-000000000010' AND version<>0
    ) OR EXISTS (
        SELECT 1 FROM task_template_items WHERE template_id='58050000-0000-4000-8000-000000000010' AND version<>0
    ) THEN
        RAISE EXCEPTION 'Contract template was edited or used; refusing to remove its history during rollback';
    END IF;

    DELETE FROM task_templates WHERE id='58050000-0000-4000-8000-000000000010';
END
$rollback$;
