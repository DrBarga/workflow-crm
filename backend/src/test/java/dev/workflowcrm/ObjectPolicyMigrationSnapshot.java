package dev.workflowcrm;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

final class ObjectPolicyMigrationSnapshot {
    static List<String> schema(Connection connection) throws Exception {
        var result = query(
                connection,
                """
                select table_name||':'||column_name||':'||data_type||':'||is_nullable||':'||coalesce(column_default,'-')
                from information_schema.columns where table_schema='public' order by table_name,ordinal_position
                """);
        result.addAll(
                query(
                        connection,
                        """
                select c.relname||':'||con.conname||':'||pg_get_constraintdef(con.oid)
                from pg_constraint con join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace
                where n.nspname='public' order by c.relname,con.conname
                """));
        result.addAll(query(
                connection,
                "select indexname||':'||indexdef from pg_indexes where schemaname='public' order by indexname"));
        return result;
    }

    static List<String> seeds(Connection connection) throws Exception {
        var result = new ArrayList<String>();
        for (String table : List.of(
                "object_statuses",
                "object_types",
                "refusal_reasons",
                "file_categories",
                "object_status_transitions",
                "object_transition_roles",
                "object_transition_required_fields")) {
            result.addAll(query(
                    connection,
                    "select (to_jsonb(t)-'created_at'-'updated_at'-'version')::text from " + table + " t order by 1"));
        }
        return result;
    }

    static List<String> query(Connection connection, String sql) throws Exception {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }
}
