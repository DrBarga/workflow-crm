ALTER TABLE users ADD COLUMN region_id uuid REFERENCES regions(id) ON DELETE RESTRICT;
CREATE INDEX ix_users_region_id ON users(region_id);
ALTER TABLE regions ADD COLUMN parent_id uuid REFERENCES regions(id) ON DELETE RESTRICT;
CREATE INDEX ix_regions_parent_id ON regions(parent_id);

UPDATE users u SET region_id=COALESCE(
    (SELECT ur.region_id FROM user_regions ur JOIN user_region_model_legacy b
        ON b.kind='USER' AND b.entity_id=u.id AND b.reference_id=ur.region_id WHERE ur.user_id=u.id),
    (SELECT ur.region_id FROM user_regions ur WHERE ur.user_id=u.id ORDER BY ur.region_id LIMIT 1));
UPDATE regions r SET parent_id=b.reference_id FROM user_region_model_legacy b
WHERE b.kind='REGION' AND b.entity_id=r.id AND EXISTS(SELECT 1 FROM regions p WHERE p.id=b.reference_id);
UPDATE roles r SET name=b.label, updated_at=clock_timestamp(), version=version+1
FROM user_region_model_legacy b WHERE b.kind='ROLE' AND b.entity_id=r.id;

-- Keep every assignment, migration snapshot and access-history array for reapplication.
