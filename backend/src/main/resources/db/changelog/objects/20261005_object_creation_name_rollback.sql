ALTER TABLE objects ADD CONSTRAINT ck_objects_name_rollback_length CHECK (char_length(name) <= 250);
ALTER TABLE objects DROP COLUMN duplicate_name;
ALTER TABLE objects ALTER COLUMN name TYPE varchar(250);
ALTER TABLE objects ADD COLUMN duplicate_name text
    GENERATED ALWAYS AS (object_search_text(name)) STORED;
CREATE INDEX ix_objects_duplicate_name ON objects USING gin (duplicate_name gin_trgm_ops);
ALTER TABLE objects DROP CONSTRAINT ck_objects_name_rollback_length;
