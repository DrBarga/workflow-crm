CREATE TABLE IF NOT EXISTS user_region_model_legacy (
    kind varchar(16) NOT NULL CHECK (kind IN ('USER', 'REGION', 'ROLE')),
    entity_id uuid NOT NULL,
    reference_id uuid,
    label text,
    PRIMARY KEY (kind, entity_id)
);

INSERT INTO user_region_model_legacy(kind, entity_id, reference_id)
SELECT 'USER', id, region_id FROM users
ON CONFLICT (kind, entity_id) DO UPDATE SET reference_id=EXCLUDED.reference_id;
INSERT INTO user_region_model_legacy(kind, entity_id, reference_id)
SELECT 'REGION', id, parent_id FROM regions
ON CONFLICT (kind, entity_id) DO UPDATE SET reference_id=EXCLUDED.reference_id;
INSERT INTO user_region_model_legacy(kind, entity_id, label)
SELECT 'ROLE', id, name FROM roles
ON CONFLICT (kind, entity_id) DO UPDATE SET label=EXCLUDED.label;

INSERT INTO user_regions(user_id, region_id)
SELECT id, region_id FROM users WHERE region_id IS NOT NULL
ON CONFLICT (user_id, region_id) DO NOTHING;

ALTER TABLE users DROP COLUMN region_id;
ALTER TABLE regions DROP COLUMN parent_id;

ALTER TABLE user_access_history ADD COLUMN IF NOT EXISTS old_region_ids uuid[];
ALTER TABLE user_access_history ADD COLUMN IF NOT EXISTS new_region_ids uuid[];
UPDATE user_access_history SET old_region_ids=CASE WHEN old_region_id IS NULL THEN '{}'::uuid[] ELSE ARRAY[old_region_id] END
WHERE old_region_ids IS NULL;
UPDATE user_access_history SET new_region_ids=CASE WHEN new_region_id IS NULL THEN '{}'::uuid[] ELSE ARRAY[new_region_id] END
WHERE new_region_ids IS NULL;

UPDATE roles SET name=CASE code
    WHEN 'MANAGER' THEN 'Менеджер'
    WHEN 'REGIONAL_MANAGER' THEN 'Провідний менеджер регіону'
    WHEN 'DEVELOPMENT_DIRECTOR' THEN 'Директор департаменту розвитку'
    WHEN 'CEO' THEN 'CEO'
    WHEN 'CONTRACT_MANAGER' THEN 'Погодження договорів'
    WHEN 'SUPERADMIN' THEN 'Адміністратор'
    END, updated_at=clock_timestamp(), version=version+1
WHERE code IN ('MANAGER','REGIONAL_MANAGER','DEVELOPMENT_DIRECTOR','CEO','CONTRACT_MANAGER','SUPERADMIN');
