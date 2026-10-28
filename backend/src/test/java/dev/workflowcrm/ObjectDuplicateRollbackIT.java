package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.*;
import java.util.*;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ObjectDuplicateRollbackIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void rollbackRestoresSchemaAndExistingExtensionSurvivesItsOwnRollback() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/pre-object-duplicates-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var original = snapshot(connection);
            var seeds = query(connection, "select code,name from object_statuses order by code");
            try (var migration = new Liquibase(
                    "db/changelog/pre-object-contacts-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(migration);
                assertThat(snapshot(connection)).isNotEqualTo(original);
                migration.rollback(3, new Contexts(), new LabelExpression());
                assertThat(snapshot(connection)).isEqualTo(original);
                update(migration);
                update(migration);
                assertThat(query(connection, "select code,name from object_statuses order by code"))
                        .isEqualTo(seeds);
                migration.rollback(3, new Contexts(), new LabelExpression());
                connection.createStatement().execute("create extension pg_trgm");
                var withExtension = snapshot(connection);
                update(migration);
                migration.rollback(2, new Contexts(), new LabelExpression());
                assertThat(snapshot(connection)).isEqualTo(withExtension);
                assertThat(query(connection, "select extname from pg_extension where extname='pg_trgm'"))
                        .hasSize(1);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }

    private List<String> snapshot(Connection connection) throws SQLException {
        var result = new ArrayList<String>();
        result.addAll(
                query(
                        connection,
                        "select table_name,column_name,is_nullable,column_default from information_schema.columns where table_schema='public' order by table_name,ordinal_position"));
        result.addAll(query(
                connection, "select indexname,indexdef from pg_indexes where schemaname='public' order by indexname"));
        result.addAll(
                query(
                        connection,
                        "select proname,pg_get_functiondef(p.oid) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' order by proname,pg_get_function_identity_arguments(p.oid)"));
        result.addAll(query(connection, "select extname from pg_extension order by extname"));
        return result;
    }

    private List<String> query(Connection connection, String sql) throws SQLException {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                var fields = new ArrayList<String>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) fields.add(rows.getString(i));
                result.add(String.join(":", fields));
            }
        }
        return result;
    }
}
