DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM object_deletion_operations WHERE state='PENDING')
    THEN RAISE EXCEPTION 'Finish pending object deletions before comment migration'; END IF;
END $$;;
ALTER TABLE object_comments ADD COLUMN mentions jsonb NOT NULL DEFAULT '[]'::jsonb
    CHECK (jsonb_typeof(mentions)='array' AND jsonb_array_length(mentions)<=500);;
CREATE OR REPLACE FUNCTION maintain_object_comment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.object_id,NEW.author_id,NEW.created_at,NEW.sequence) IS DISTINCT FROM
       (OLD.id,OLD.object_id,OLD.author_id,OLD.created_at,OLD.sequence)
    THEN RAISE EXCEPTION 'Comment identity is immutable' USING ERRCODE='23514'; END IF;
    IF NEW.body IS DISTINCT FROM OLD.body OR NEW.mentions IS DISTINCT FROM OLD.mentions THEN
        NEW.version := OLD.version+1;
        NEW.edited_at := clock_timestamp();
        NEW.updated_at := NEW.edited_at;
    ELSE
        NEW.version := OLD.version;
        NEW.edited_at := OLD.edited_at;
        NEW.updated_at := OLD.updated_at;
    END IF;
    RETURN NEW;
END $$;;
