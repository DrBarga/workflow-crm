package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ObjectLiquibaseRollbackIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void rollbackRestoresPreviousSchemaAndPreservesExistingUserData() throws Exception {
        String schema = "rollback_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(schema)) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var migration =
                    new Liquibase("db/changelog/pre-objects-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                migration.update(new Contexts(), new LabelExpression());
            }
        }
        try (Connection connection = connection(schema)) {
            var originalSchema = schemaSnapshot(connection, schema);
            execute(
                    connection,
                    "insert into users(email, password_hash, full_name, role_id, status) "
                            + "select 'preserved@example.com', 'test-hash', 'Existing User', id, 'ACTIVE' from roles where code = 'MANAGER'");
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var migration = new Liquibase(
                    "db/changelog/pre-object-creation-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(schemaSnapshot(connection, schema)).isNotEqualTo(originalSchema);
                migration.rollback(4, new Contexts(), new LabelExpression());
                assertThat(schemaSnapshot(connection, schema)).containsExactlyElementsOf(originalSchema);
                assertThat(scalar(connection, "select count(*) from users where email = 'preserved@example.com'"))
                        .isEqualTo(1);
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(scalar(connection, "select count(*) from object_statuses"))
                        .isEqualTo(9);
                assertThat(scalar(connection, "select count(*) from file_categories"))
                        .isEqualTo(5);
            }
        }
    }

    @Test
    void upgradesPublishedObjectSeedAndRollsBackOnlyWorkflowRevision() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(schema)) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var migration = new Liquibase(
                    "db/changelog/pre-object-workflow-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
            }
        }
        try (Connection connection = connection(schema)) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            var original = seedSnapshot(connection);
            var originalSchema = schemaSnapshot(connection, schema);
            try (var migration = new Liquibase(
                    "db/changelog/pre-object-creation-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(scalar(connection, "select count(*) from object_status_transitions"))
                        .isEqualTo(47);
                assertThat(
                                scalar(
                                        connection,
                                        "select count(*) from object_status_transitions where code = 'OPEN_OBJECT' and is_active"))
                        .isEqualTo(1);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(seedSnapshot(connection)).containsExactlyElementsOf(original);
                assertThat(schemaSnapshot(connection, schema)).containsExactlyElementsOf(originalSchema);
                assertThat(
                                scalar(
                                        connection,
                                        "select count(*) from object_status_transitions where code = 'OPEN_OBJECT' and not is_active"))
                        .isEqualTo(1);
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(scalar(connection, "select count(*) from object_status_transitions"))
                        .isEqualTo(47);
            }
        }
    }

    @Test
    void secondRunPreservesSeedIdsCountsAndChangelogAndDoesNotDuplicateData() throws Exception {
        String schema = "repeat_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(schema)) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var migration = new Liquibase(
                    "db/changelog/pre-object-creation-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                var before = seedSnapshot(connection);
                int changesets = scalar(connection, "select count(*) from databasechangelog");
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(seedSnapshot(connection)).containsExactlyElementsOf(before);
                assertThat(scalar(connection, "select count(*) from databasechangelog"))
                        .isEqualTo(changesets);
            }
        }
    }

    private Connection connection(String schema) throws Exception {
        var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        execute(connection, "create schema if not exists " + schema);
        connection.setSchema(schema);
        return connection;
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var results = statement.executeQuery(sql)) {
            results.next();
            return results.getInt(1);
        }
    }

    private List<String> schemaSnapshot(Connection connection, String schema) throws Exception {
        var snapshot = new ArrayList<String>();
        try (var statement = connection.prepareStatement(
                "select table_name, column_name, data_type, is_nullable, column_default "
                        + "from information_schema.columns where table_schema = ? order by table_name, ordinal_position")) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                while (rows.next())
                    snapshot.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3) + ":"
                            + rows.getString(4) + ":" + rows.getString(5));
            }
        }
        try (var statement = connection.prepareStatement(
                "select table_name, constraint_name, constraint_type "
                        + "from information_schema.table_constraints where table_schema = ? order by table_name, constraint_name")) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) snapshot.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3));
            }
        }
        return snapshot;
    }

    private List<String> seedSnapshot(Connection connection) throws Exception {
        var snapshot = new ArrayList<String>();
        for (String table : List.of(
                "object_statuses", "object_types", "refusal_reasons", "file_categories", "object_status_transitions")) {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("select id, code from " + table + " order by code")) {
                while (rows.next()) snapshot.add(table + ":" + rows.getString(1) + ":" + rows.getString(2));
            }
        }
        for (String table : List.of("object_transition_roles", "object_transition_required_fields")) {
            snapshot.add(table + ":" + scalar(connection, "select count(*) from " + table));
        }
        return snapshot;
    }

    private void clearCache() {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
    }
}
