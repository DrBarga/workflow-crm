CREATE TABLE user_regions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    region_id uuid NOT NULL REFERENCES regions(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_user_regions UNIQUE(user_id, region_id)
);
CREATE INDEX ix_user_regions_region ON user_regions(region_id);
CREATE TABLE region_boundaries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    region_id uuid NOT NULL UNIQUE REFERENCES regions(id) ON DELETE CASCADE,
    geometry jsonb NOT NULL CHECK (jsonb_typeof(geometry)='object' AND jsonb_exists(geometry,'type') AND jsonb_exists(geometry,'coordinates')
        AND geometry->>'type' IN ('Polygon','MultiPolygon')
        AND jsonb_typeof(geometry->'coordinates')='array'),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
