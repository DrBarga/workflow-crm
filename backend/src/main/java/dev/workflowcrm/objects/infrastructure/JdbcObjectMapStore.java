package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectMapStore implements ObjectMapStore {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcObjectMapStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> find(
            ObjectListFilter filter, ObjectListScope scope, ObjectMapBounds bounds, Instant snapshot, int limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("actor", scope.actorId())
                .addValue("limit", limit)
                .addValue("west", bounds.west())
                .addValue("south", bounds.south())
                .addValue("east", bounds.east())
                .addValue("north", bounds.north());
        String sql =
                "select o.id,o.manager_id,o.region_id,o.name,o.entrance_longitude,o.entrance_latitude,s.code as status"
                        + " from objects o join object_statuses s on s.id=o.status_id"
                        + " left join object_types t on t.id=o.object_type_id"
                        + ObjectListPredicates.where(filter, scope, snapshot, parameters)
                        + " and o.entrance_latitude between :south and :north and o.entrance_longitude between :west and :east"
                        + " order by o.id limit :limit";
        return jdbc.query(
                sql,
                parameters,
                (r, n) -> new Row(
                        r.getObject("id", UUID.class),
                        r.getObject("manager_id", UUID.class),
                        r.getObject("region_id", UUID.class),
                        r.getString("name"),
                        new CreatedObjectView.Coordinates(
                                r.getBigDecimal("entrance_longitude"), r.getBigDecimal("entrance_latitude")),
                        r.getString("status")));
    }
}
