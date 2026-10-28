package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class ObjectListRowMapper {
    private ObjectListRowMapper() {}

    static ObjectListStore.Row read(ResultSet rs, ObjectListFilter filter, ObjectListScope scope) throws SQLException {
        var entrance = new CreatedObjectView.Coordinates(
                rs.getBigDecimal("entrance_longitude"), rs.getBigDecimal("entrance_latitude"));
        ObjectListItem restricted = scope.contract()
                ? new ObjectListItem.Technical(
                        rs.getString("name"), entrance, technical(rs), rs.getString("technical_specifications"))
                : new ObjectListItem.Pin(rs.getString("name"), entrance);
        String value = filter.sort() == ObjectListFilter.Sort.CREATED_AT
                ? rs.getTimestamp("sort_value").toInstant().toString()
                : rs.getString("sort_value");
        return new ObjectListStore.Row(
                rs.getObject("id", UUID.class),
                value,
                rs.getObject("manager_id", UUID.class),
                rs.getObject("region_id", UUID.class),
                restricted);
    }

    private static TechnicalSpecifications technical(ResultSet r) throws SQLException {
        return new TechnicalSpecifications(
                r.getBigDecimal("building_area"),
                r.getBigDecimal("first_floor_area"),
                r.getBigDecimal("second_floor_area"),
                r.getBigDecimal("basement_area"),
                r.getBigDecimal("sales_hall_area"),
                r.getBigDecimal("floor_slab_height"),
                r.getBigDecimal("connected_power"),
                r.getBigDecimal("water_supply_flow"),
                r.getString("power_connection_category") == null
                        ? null
                        : PowerConnectionCategory.valueOf(r.getString("power_connection_category")),
                r.getString("sewage_type") == null ? null : SewageType.valueOf(r.getString("sewage_type")));
    }
}
