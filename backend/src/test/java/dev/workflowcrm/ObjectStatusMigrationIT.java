package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ObjectStatusMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesDataRestoresConfigurationAndReappliesIdempotently() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/ic-task-policy-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            fixture.jdbc.update(
                    "insert into tasks(object_id,source_status_id,name,assignee_id,deadline,author_id) select ?,id,'Задача',?,'2026-10-04',? from object_statuses where code='FOUND'",
                    fixture.object,
                    fixture.manager,
                    fixture.manager);
            connection.commit();
            var seeds = ObjectPolicyMigrationSnapshot.seeds(connection);
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var object = fixture.objectSnapshot(fixture.object);
            var tasks = fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/object-status-execution-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select unfinished_task_action from object_status_transitions where code like 'IC_%' and is_system_only order by code",
                                String.class))
                        .containsOnly("NONE");
                var applied = ObjectPolicyMigrationSnapshot.seeds(connection);
                update(migration);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(applied);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                assertThat(fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t", String.class))
                        .isEqualTo(tasks);
                update(migration);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                assertThat(fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t", String.class))
                        .isEqualTo(tasks);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
