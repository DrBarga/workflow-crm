INSERT INTO task_template_versions(template_id,revision,fingerprint,trigger_status_id,contract_type_id,legacy_contract_type)
SELECT t.id,row_number() OVER(PARTITION BY t.id ORDER BY s.status_id),
    'legacy:'||md5(t.id::text||s.status_id::text),s.status_id,t.contract_type_id,t.contract_type
FROM task_templates t JOIN (
    SELECT id AS template_id,trigger_status_id AS status_id FROM task_templates
    UNION SELECT i.template_id,k.source_status_id FROM tasks k JOIN task_template_items i ON i.id=k.template_item_id
) s ON s.template_id=t.id;
INSERT INTO template_items(template_version_id,source_item_id,name,deadline_rule,deadline_offset_days,assignment_rule,assignee_id,sort_order)
SELECT v.id,i.id,i.name,'FROM_START_CALENDAR',i.deadline_offset_days,'EXPLICIT_USER',i.assignee_id,i.sort_order
FROM task_template_items i JOIN task_template_versions v ON v.template_id=i.template_id;

INSERT INTO task_sets(object_id,template_version_id,trigger_status_id,state,created_at,updated_at)
SELECT t.object_id,v.id,t.source_status_id,
    CASE WHEN bool_and(t.status='DONE') THEN 'COMPLETED'
         WHEN bool_and(t.status IN ('DONE','CANCELLED')) THEN 'CANCELLED' ELSE 'ACTIVE' END,
    min(t.created_at),max(t.updated_at)
FROM tasks t LEFT JOIN task_template_items i ON i.id=t.template_item_id
LEFT JOIN task_template_versions v ON v.template_id=i.template_id AND v.trigger_status_id=t.source_status_id
GROUP BY t.object_id,v.id,t.source_status_id;
UPDATE tasks t SET task_set_id=s.id,template_version_id=v.id,source_item_id=i.id,
    completed_at=CASE WHEN t.status='DONE' THEN t.updated_at ELSE NULL END
FROM task_sets s LEFT JOIN task_template_versions v ON v.id=s.template_version_id
LEFT JOIN template_items i ON i.template_version_id=v.id
WHERE s.object_id=t.object_id AND s.trigger_status_id=t.source_status_id
AND ((i.source_item_id=t.template_item_id) OR (s.template_version_id IS NULL AND t.template_item_id IS NULL));
