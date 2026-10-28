CREATE FUNCTION object_search_text(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT btrim(regexp_replace(lower(COALESCE(value, '')), '[^[:alnum:]]+', ' ', 'g'))
$$;

CREATE FUNCTION object_distance_metres(lon1 double precision, lat1 double precision,
                                      lon2 double precision, lat2 double precision) RETURNS double precision
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT 2 * 6371008.8 * asin(sqrt(least(1.0,
        power(sin(radians(lat2 - lat1) / 2), 2)
        + cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lon2 - lon1) / 2), 2))))
$$;

ALTER TABLE objects
    ADD COLUMN duplicate_name text GENERATED ALWAYS AS (object_search_text(name)) STORED,
    ADD COLUMN duplicate_address text GENERATED ALWAYS AS (object_search_text(address)) STORED;
CREATE INDEX ix_objects_duplicate_name ON objects USING gin (duplicate_name gin_trgm_ops);
CREATE INDEX ix_objects_duplicate_address ON objects (duplicate_address);
CREATE INDEX ix_objects_entrance_coordinates ON objects (entrance_latitude, entrance_longitude);
