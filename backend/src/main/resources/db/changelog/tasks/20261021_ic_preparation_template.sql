INSERT INTO task_templates(id,trigger_status_id)
SELECT '58050000-0000-4000-8000-000000000000',id FROM object_statuses WHERE code='IC_PREPARATION';

INSERT INTO task_template_items(id,template_id,name,deadline_offset_days,sort_order,assignment_rule,
    role_id,needs_review,result_recipient_role_id)
SELECT matrix.id::uuid,'58050000-0000-4000-8000-000000000000',matrix.name,matrix.days,matrix.position,
    matrix.assignment,assignee.id,matrix.review,recipient.id
FROM (VALUES
    ('58050000-0000-4000-8000-000000000001','Заповнити карточку об''єкта',3,0,'OBJECT_MANAGER',NULL,true,'REGIONAL_MANAGER'),
    ('58050000-0000-4000-8000-000000000002','Підготувати презентацію',3,1,'OBJECT_MANAGER',NULL,true,'REGIONAL_MANAGER'),
    ('58050000-0000-4000-8000-000000000003','Отримати прорахунок ШІ',5,2,'OBJECT_MANAGER',NULL,true,'REGIONAL_MANAGER'),
    ('58050000-0000-4000-8000-000000000004','Скласти модель',5,3,'REGIONAL_ROLE','REGIONAL_MANAGER',false,'DEVELOPMENT_DIRECTOR')
) AS matrix(id,name,days,position,assignment,assignee_role,review,recipient_role)
LEFT JOIN roles assignee ON assignee.code=matrix.assignee_role
JOIN roles recipient ON recipient.code=matrix.recipient_role;

INSERT INTO task_template_item_dependencies(template_id,item_id,predecessor_id)
VALUES ('58050000-0000-4000-8000-000000000000','58050000-0000-4000-8000-000000000004',
    '58050000-0000-4000-8000-000000000001');
