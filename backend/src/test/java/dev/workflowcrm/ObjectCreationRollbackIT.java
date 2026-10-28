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
class ObjectCreationRollbackIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void creationMigrationRollsBackToPublishedModelAndReappliesWithoutDuplicatingSeeds() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/pre-object-creation-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var columns = snapshot(connection);
            var seeds = seeds(connection);
            try (var migration = new Liquibase(
                    "db/changelog/object-creation-migration-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(snapshot(connection)).isNotEqualTo(columns);
                assertThat(seeds(connection)).isEqualTo(seeds);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(snapshot(connection)).isEqualTo(columns);
                assertThat(seeds(connection)).isEqualTo(seeds);
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(seeds(connection)).isEqualTo(seeds);
            }
        }
    }

    private List<String> snapshot(Connection connection) throws SQLException {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "select table_name,column_name,is_nullable,column_default from information_schema.columns where table_schema='public' order by table_name,ordinal_position")) {
            while (rows.next())
                result.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3) + ":"
                        + rows.getString(4));
        }
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "select c.relname,con.conname,pg_get_constraintdef(con.oid) from pg_constraint con join pg_class c on c.oid=con.conrelid "
                                + "join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' order by c.relname,con.conname")) {
            while (rows.next()) result.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3));
        }
        return result;
    }

    private List<String> seeds(Connection connection) throws SQLException {
        var result = new ArrayList<String>();
        for (String table : List.of(
                "object_statuses", "object_types", "refusal_reasons", "file_categories", "object_status_transitions")) {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("select id,code from " + table + " order by code")) {
                while (rows.next()) result.add(table + ":" + rows.getString(1) + ":" + rows.getString(2));
            }
        }
        return result;
    }

    private void clearCache() {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
    }
}
