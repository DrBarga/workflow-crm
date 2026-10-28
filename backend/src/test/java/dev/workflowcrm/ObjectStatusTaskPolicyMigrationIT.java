package dev.workflowcrm;

import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.*;
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
class ObjectStatusTaskPolicyMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void appliesOnceRestoresPreviousPolicyAndPreservesObjectData() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/status-task-policy-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            connection.commit();
            var original = seeds(connection);
            var originalSchema = schema(connection);
            var object = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261015_status_task_policy.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select unfinished_task_action from object_status_transitions where is_active",
                                String.class))
                        .doesNotContain("BLOCK");
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='IC_REFUSED'",
                                String.class))
                        .isEqualTo("CANCEL_ALL");
                var applied = seeds(connection);
                update(migration);
                assertThat(seeds(connection)).isEqualTo(applied);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(seeds(connection)).isEqualTo(original);
                assertThat(schema(connection)).isEqualTo(originalSchema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                update(migration);
                assertThat(seeds(connection)).isEqualTo(applied);
                fixture.jdbc.update(
                        "update object_status_transitions set unfinished_task_action='CANCEL',version=version+1 where code='SIGN_CONTRACT'");
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='SIGN_CONTRACT'",
                                String.class))
                        .isEqualTo("CANCEL");
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
