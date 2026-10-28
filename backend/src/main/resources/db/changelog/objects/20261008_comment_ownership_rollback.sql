DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM object_comments) OR EXISTS (SELECT 1 FROM task_comments)
    THEN RAISE EXCEPTION 'Comments must be migrated before ownership rollback'; END IF;
END $$;;
DROP TABLE task_comments;;
DROP TABLE object_comments;;
