WITH input AS (
    SELECT CAST(:longitude AS double precision) AS longitude,
           CAST(:latitude AS double precision) AS latitude,
           object_search_text(CAST(:address AS text)) AS address,
           object_search_text(CAST(:name AS text)) AS name
), candidates AS (
    SELECT id FROM objects
    WHERE entrance_latitude BETWEEN :south AND :north
      AND entrance_longitude BETWEEN :west AND :east
    UNION
    SELECT o.id FROM objects o, input i
    WHERE i.address <> '' AND o.duplicate_address = i.address
    UNION
    SELECT o.id FROM objects o, input i
    WHERE i.name <> '' AND o.duplicate_name % i.name
), scored AS (
    SELECT o.id, o.name, o.address, o.manager_id, o.region_id,
           s.code AS status, s.name AS status_name,
           object_distance_metres(o.entrance_longitude, o.entrance_latitude,
                                  i.longitude, i.latitude) AS distance,
           (i.address <> '' AND o.duplicate_address = i.address)
           OR (i.name <> '' AND o.duplicate_name % i.name) AS text_match
    FROM candidates c JOIN objects o ON o.id = c.id
    JOIN object_statuses s ON s.id = o.status_id CROSS JOIN input i
)
SELECT id, name, address, manager_id, region_id, status, status_name, distance,
       count(*) OVER () AS total
FROM scored WHERE distance <= :radius OR text_match
ORDER BY distance, id LIMIT 20
