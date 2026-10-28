DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM files)
    THEN RAISE EXCEPTION 'File records must be migrated before ownership rollback'; END IF;
END $$;;
DROP TABLE files;;
