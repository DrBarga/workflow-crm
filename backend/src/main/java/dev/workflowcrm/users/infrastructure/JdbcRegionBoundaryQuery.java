package dev.workflowcrm.users.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.shared.geography.GeoJsonBoundary;
import dev.workflowcrm.users.api.RegionBoundaryQuery;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRegionBoundaryQuery implements RegionBoundaryQuery {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcRegionBoundaryQuery(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<UUID> locate(BigDecimal longitude, BigDecimal latitude) {
        var matches = jdbc
                .query("select region_id,geometry::text from region_boundaries order by region_id", (row, n) -> {
                    try {
                        var boundary = new GeoJsonBoundary(json.readTree(row.getString(2)));
                        return boundary.contains(longitude.doubleValue(), latitude.doubleValue())
                                ? row.getObject(1, UUID.class)
                                : null;
                    } catch (java.io.IOException | IllegalArgumentException exception) {
                        return null;
                    }
                })
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
