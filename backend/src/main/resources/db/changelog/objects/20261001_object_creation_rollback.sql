ALTER TABLE objects ALTER COLUMN contact_name SET NOT NULL;
ALTER TABLE objects ALTER COLUMN contact_role SET NOT NULL;
ALTER TABLE objects DROP CONSTRAINT ck_objects_contact_channel;
ALTER TABLE objects ADD CONSTRAINT ck_objects_contact_channel CHECK
    (COALESCE(btrim(contact_phone), '') <> '' OR COALESCE(btrim(contact_email), '') <> '');
DROP TABLE object_creation_requests;
