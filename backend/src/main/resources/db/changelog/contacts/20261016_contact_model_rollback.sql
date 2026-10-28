DROP TABLE contact_phone_views;
DROP INDEX ix_contacts_name;
DROP INDEX ix_contacts_normalized_phone;
DROP INDEX ix_contacts_type;
DROP INDEX ix_contacts_author;
ALTER TABLE contacts DROP COLUMN normalized_phone, DROP COLUMN contact_type_id,
    DROP COLUMN author_id, DROP COLUMN comment;
DROP TABLE contact_types;
