package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectListStore implements ObjectListStore {
    private static final String COLUMNS =
            "o.id,o.manager_id,o.region_id,o.name,o.entrance_longitude,o.entrance_latitude";
    private static final String TECHNICAL = ",o.building_area,o.first_floor_area,o.second_floor_area,o.basement_area,"
            + "o.sales_hall_area,o.floor_slab_height,o.connected_power,o.water_supply_flow,"
            + "o.power_connection_category,o.sewage_type,o.technical_specifications";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectListFullReader fullReader;

    JdbcObjectListStore(NamedParameterJdbcTemplate jdbc, ObjectListFullReader fullReader) {
        this.jdbc = jdbc;
        this.fullReader = fullReader;
    }

    public List<Row> find(ObjectListFilter filter, ObjectListScope scope, ObjectListPosition position) {
        var order = ObjectListOrder.of(filter);
        var p = new MapSqlParameterSource().addValue("actor", scope.actorId()).addValue("limit", filter.limit() + 1);
        String sql = "select " + COLUMNS + (scope.contract() ? TECHNICAL : "") + "," + order.column()
                + " as sort_value from objects o join object_statuses s on s.id=o.status_id"
                + " left join object_types t on t.id=o.object_type_id"
                + ObjectListPredicates.where(filter, scope, position, p) + order.after(position, p)
                + " order by " + order.ordering() + " limit :limit";
        return jdbc.query(sql, p, (r, n) -> ObjectListRowMapper.read(r, filter, scope));
    }

    public Map<UUID, ObjectListItem.Full> full(List<UUID> ids) {
        return fullReader.read(ids);
    }
}
