DO $seed$
BEGIN
    IF EXISTS (
        SELECT 1 FROM task_seed_templates t
        LEFT JOIN object_statuses s ON s.code=t.status_code
        LEFT JOIN contract_types c ON c.code=t.contract_type_code
        WHERE s.id IS NULL OR (t.contract_type_code IS NOT NULL AND c.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'Task template seed: unknown status or contract type';
    END IF;

    IF EXISTS (
        SELECT 1 FROM task_seed_templates t
        WHERE NOT EXISTS (SELECT 1 FROM task_seed_items i WHERE i.template_id=t.id)
    ) THEN
        RAISE EXCEPTION 'Task template seed: a template must contain tasks';
    END IF;

    IF EXISTS (
        SELECT 1 FROM task_seed_items i
        WHERE NOT COALESCE(
            (i.assignment='EXPLICIT_USER' AND i.assignee_id IS NOT NULL AND i.assignee_role IS NULL)
            OR (i.assignment='OBJECT_MANAGER' AND i.assignee_id IS NULL AND i.assignee_role IS NULL)
            OR (i.assignment IN ('REGIONAL_ROLE','NETWORK_ROLE') AND i.assignee_id IS NULL AND i.assignee_role IS NOT NULL),
            false)
    ) THEN
        RAISE EXCEPTION 'Task template seed: each task needs a valid assignee or assignment rule';
    END IF;

    IF EXISTS (
        SELECT 1 FROM task_seed_items i
        LEFT JOIN roles a ON a.code=i.assignee_role
        LEFT JOIN roles r ON r.code=i.recipient_role
        LEFT JOIN users u ON u.id=i.assignee_id
        WHERE (i.assignee_role IS NOT NULL AND a.id IS NULL)
           OR (i.recipient_role IS NOT NULL AND r.id IS NULL)
           OR (i.assignee_id IS NOT NULL AND u.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'Task template seed: unknown assignee or role';
    END IF;

    IF EXISTS (
        WITH RECURSIVE paths(item_id,predecessor_id) AS (
            SELECT item_id,predecessor_id FROM task_seed_dependencies
            UNION
            SELECT p.item_id,d.predecessor_id
            FROM paths p JOIN task_seed_dependencies d ON d.item_id=p.predecessor_id
        ) SELECT 1 FROM paths WHERE item_id=predecessor_id
    ) THEN
        RAISE EXCEPTION 'Task template seed: cyclic dependencies';
    END IF;
END
$seed$;

INSERT INTO task_templates(id,trigger_status_id,contract_type_id,contract_type)
SELECT t.id,s.id,c.id,c.code FROM task_seed_templates t
JOIN object_statuses s ON s.code=t.status_code
LEFT JOIN contract_types c ON c.code=t.contract_type_code;

INSERT INTO task_template_items(id,template_id,name,deadline_offset_days,sort_order,assignment_rule,
    assignee_id,role_id,needs_review,result_recipient_role_id,escalation_days)
SELECT i.id,i.template_id,i.name,i.days,i.position,i.assignment,
    i.assignee_id,a.id,i.needs_review,r.id,i.escalation_days
FROM task_seed_items i
LEFT JOIN roles a ON a.code=i.assignee_role
LEFT JOIN roles r ON r.code=i.recipient_role;

INSERT INTO task_template_item_dependencies(template_id,item_id,predecessor_id)
SELECT template_id,item_id,predecessor_id FROM task_seed_dependencies;
