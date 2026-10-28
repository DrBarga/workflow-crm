INSERT INTO task_seed_templates(id,status_code,contract_type_code)
VALUES ('58050000-0000-4000-8000-000000000010','CONTRACT_APPROVAL',NULL);

INSERT INTO task_seed_items(id,template_id,name,days,position,assignment,assignee_role,
    needs_review,recipient_role,escalation_days)
VALUES
    ('58050000-0000-4000-8000-000000000011','58050000-0000-4000-8000-000000000010',
     'Погодити опитувальний лист',3,0,'REGIONAL_ROLE','REGIONAL_MANAGER',false,'DEVELOPMENT_DIRECTOR',1),
    ('58050000-0000-4000-8000-000000000012','58050000-0000-4000-8000-000000000010',
     'Погодити фінансову модель',3,1,'REGIONAL_ROLE','REGIONAL_MANAGER',false,'DEVELOPMENT_DIRECTOR',1),
    ('58050000-0000-4000-8000-000000000013','58050000-0000-4000-8000-000000000010',
     'Погодити договір в системі Lotus',3,2,'NETWORK_ROLE','CONTRACT_MANAGER',false,'DEVELOPMENT_DIRECTOR',1);
