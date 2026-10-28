alter table contact_history
    add column changed_values jsonb not null default '{}'::jsonb,
    add constraint chk_contact_history_values check (
        jsonb_typeof(changed_values) = 'object'
        and changed_values - array['name','role','phone','email','contactTypeId','comment'] = '{}'::jsonb
    );
