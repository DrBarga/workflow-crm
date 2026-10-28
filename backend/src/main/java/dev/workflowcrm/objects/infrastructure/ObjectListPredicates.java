package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class ObjectListPredicates {
    private ObjectListPredicates() {}

    static String where(
            ObjectListFilter f, ObjectListScope scope, ObjectListPosition position, MapSqlParameterSource p) {
        return where(f, scope, position.snapshot(), p);
    }

    static String where(ObjectListFilter f, ObjectListScope scope, Instant snapshot, MapSqlParameterSource p) {
        var sql = new StringBuilder(" where o.created_at <= :snapshot and not exists "
                + "(select 1 from object_deletion_operations d where d.object_id=o.id and d.state='PENDING')");
        p.addValue("snapshot", Timestamp.from(snapshot));
        if (scope.regionRestricted()) {
            sql.append(" and o.region_id in (:regions)");
            p.addValue("regions", scope.regionIds());
        }
        equal(sql, p, "s.code", "status", f.status());
        equal(sql, p, "o.manager_id", "manager", f.managerId());
        equal(sql, p, "o.region_id", "region", f.regionId());
        equal(sql, p, "t.code", "type", f.objectType());
        if (f.createdFrom() != null) {
            sql.append(" and o.created_at >= :from");
            p.addValue("from", Timestamp.from(f.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (f.createdTo() != null) {
            sql.append(" and o.created_at < :to");
            p.addValue(
                    "to",
                    Timestamp.from(f.createdTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (f.q() != null) {
            p.addValue(
                    "search",
                    "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            sql.append(" and (o.name ilike :search escape '\\'");
            if (!scope.contract()) {
                sql.append(" or (");
                if (scope.role().equals("MANAGER")) sql.append("o.manager_id=:actor and ");
                if (scope.role().equals("REGIONAL_MANAGER")) {
                    sql.append(scope.regionIds().isEmpty() ? "false and " : "o.region_id in (:regions) and ");
                    if (!scope.regionIds().isEmpty()) p.addValue("regions", scope.regionIds());
                }
                sql.append("o.address ilike :search escape '\\')");
            }
            sql.append(")");
        }
        return sql.toString();
    }

    static String forExport(ObjectListFilter f, ObjectListScope scope, Instant snapshot, MapSqlParameterSource p) {
        String sql = where(f, scope, snapshot, p);
        if (!scope.role().equals("REGIONAL_MANAGER")) return sql;
        if (scope.regionIds().isEmpty()) return sql + " and false";
        p.addValue("regions", scope.regionIds());
        return sql + " and o.region_id in (:regions)";
    }

    private static void equal(StringBuilder sql, MapSqlParameterSource p, String column, String key, Object value) {
        if (value == null) return;
        sql.append(" and ").append(column).append("=:").append(key);
        p.addValue(key, value);
    }
}
