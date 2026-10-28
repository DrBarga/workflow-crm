DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM file_upload_reservations)
    THEN RAISE EXCEPTION 'Clean up pending uploads before rolling back files'; END IF;
END $$;;
DROP TABLE file_upload_reservations;;
DROP INDEX ix_files_object_sequence;;
DROP INDEX ux_files_sequence;;
ALTER TABLE files DROP COLUMN sequence;;
