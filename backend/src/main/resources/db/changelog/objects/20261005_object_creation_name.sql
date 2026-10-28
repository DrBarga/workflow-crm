ALTER TABLE objects DROP COLUMN duplicate_name;
ALTER TABLE objects ALTER COLUMN name TYPE varchar(500);
ALTER TABLE objects ADD COLUMN duplicate_name text
    GENERATED ALWAYS AS (object_search_text(name)) STORED;
CREATE INDEX ix_objects_duplicate_name ON objects USING gin (duplicate_name gin_trgm_ops);
