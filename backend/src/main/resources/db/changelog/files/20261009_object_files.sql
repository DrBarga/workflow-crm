DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM object_deletion_operations WHERE state='PENDING')
    THEN RAISE EXCEPTION 'Complete pending object deletions before migrating files'; END IF;
END $$;;

ALTER TABLE files ADD COLUMN sequence bigint;;
WITH ordered AS (SELECT id,row_number() OVER (ORDER BY created_at,id) AS position FROM files)
UPDATE files SET sequence=ordered.position FROM ordered WHERE files.id=ordered.id;;
ALTER TABLE files ALTER COLUMN sequence SET NOT NULL;;
ALTER TABLE files ALTER COLUMN sequence ADD GENERATED ALWAYS AS IDENTITY;;
SELECT setval(pg_get_serial_sequence('files','sequence'),greatest(coalesce(max(sequence),0),1),count(*)>0) FROM files;;
CREATE UNIQUE INDEX ux_files_sequence ON files(sequence);;
CREATE INDEX ix_files_object_sequence ON files(object_id,sequence) WHERE object_id IS NOT NULL AND deleted_at IS NULL;;

CREATE TABLE file_upload_reservations (
    id uuid PRIMARY KEY,
    bucket varchar(63) NOT NULL,
    storage_key varchar(1024) NOT NULL,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP+interval '10 minutes',
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts>=0),
    UNIQUE(bucket,storage_key)
);;
CREATE INDEX ix_file_upload_cleanup ON file_upload_reservations(next_attempt_at,id);;
