alter table contact_history
    drop constraint chk_contact_history_values,
    drop column changed_values;
