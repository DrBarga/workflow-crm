DROP INDEX ix_objects_entrance_coordinates;
ALTER TABLE objects DROP COLUMN duplicate_address, DROP COLUMN duplicate_name;
DROP FUNCTION object_distance_metres(double precision, double precision, double precision, double precision);
DROP FUNCTION object_search_text(text);
