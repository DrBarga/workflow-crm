UPDATE objects object SET contact_name=contact.name, contact_role=contact.role,
    contact_phone=contact.phone, contact_email=contact.email
FROM object_creation_requests request JOIN contacts contact ON contact.id=request.contact_id
WHERE request.object_id=object.id AND object.contact_name IS NULL;
ALTER TABLE object_creation_requests DROP COLUMN contact_id;
DROP TABLE object_contacts;
DROP TABLE contacts;
