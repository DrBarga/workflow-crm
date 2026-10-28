package dev.workflowcrm;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ObjectRegionBoundaryFixture {
    static final String POLYGON =
            """
            {"type":"Polygon","coordinates":[[[30,50],[31,50],[31,51],[30,51],[30,50]]]}
            """;

    static void load(JdbcTemplate jdbc, UUID region) {
        jdbc.update("insert into region_boundaries(region_id,geometry) values(?,?::jsonb)", region, POLYGON);
    }
}
