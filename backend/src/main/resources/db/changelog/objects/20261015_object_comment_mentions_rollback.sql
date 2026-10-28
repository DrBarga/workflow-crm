CREATE OR REPLACE FUNCTION maintain_object_comment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.object_id,NEW.author_id,NEW.created_at,NEW.sequence) IS DISTINCT FROM
       (OLD.id,OLD.object_id,OLD.author_id,OLD.created_at,OLD.sequence)
    THEN RAISE EXCEPTION 'Comment identity is immutable' USING ERRCODE='23514'; END IF;
    IF NEW.body IS DISTINCT FROM OLD.body THEN
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
ALTER TABLE object_comments DROP COLUMN mentions;;
