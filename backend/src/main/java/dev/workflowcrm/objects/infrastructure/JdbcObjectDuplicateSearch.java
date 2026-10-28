package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectDuplicateSearch;
import dev.workflowcrm.objects.domain.ObjectRegistration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@EnableConfigurationProperties(ObjectDuplicateProperties.class)
class JdbcObjectDuplicateSearch implements ObjectDuplicateSearch {
    private final JdbcTemplate jdbc;
    private final ObjectDuplicateProperties settings;
    private final String search;

    JdbcObjectDuplicateSearch(JdbcTemplate jdbc, ObjectDuplicateProperties settings) throws IOException {
        this.jdbc = jdbc;
        this.settings = settings;
        search = new ClassPathResource("queries/object-duplicates.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    public Matches find(ObjectRegistration registration) {
        jdbc.queryForObject(
                "select set_config('pg_trgm.similarity_threshold',?,true)",
                String.class,
                Double.toString(settings.nameSimilarity()));
        double latitude = registration.entrance().getLatitude().doubleValue();
        double longitude = registration.entrance().getLongitude().doubleValue();
        double latitudeDelta = Math.toDegrees(settings.radiusMetres() / 6371008.8);
        double longitudeDelta = latitudeDelta / Math.cos(Math.toRadians(latitude + latitudeDelta));
        var parameters = new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radius", settings.radiusMetres())
                .addValue("south", latitude - latitudeDelta)
                .addValue("north", latitude + latitudeDelta)
                .addValue("west", longitude - longitudeDelta)
                .addValue("east", longitude + longitudeDelta)
                .addValue("address", registration.address())
                .addValue("name", registration.name());
        var rows = new NamedParameterJdbcTemplate(jdbc)
                .query(
                        search,
                        parameters,
                        (rs, n) -> new Row(
                                rs.getLong("total"),
                                new Candidate(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("address"),
                                        rs.getString("status"),
                                        rs.getString("status_name"),
                                        rs.getObject("manager_id", UUID.class),
                                        rs.getObject("region_id", UUID.class),
                                        rs.getDouble("distance"))));
        return new Matches(
                rows.isEmpty() ? 0 : rows.getFirst().total(),
                rows.stream().map(Row::candidate).toList());
    }

    private record Row(long total, Candidate candidate) {}
}
