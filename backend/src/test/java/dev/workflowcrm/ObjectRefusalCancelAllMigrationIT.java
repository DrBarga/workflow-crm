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
class ObjectRefusalCancelAllMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void repeatsWithoutDuplicatesAndRestoresOriginalRulesWithoutChangingObjects() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/object-refusal-cancel-all-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            fixture.jdbc.update(
                    "update object_status_transitions set unfinished_task_action='NONE' where code='MANAGER_FOUND_TO_REFUSED'");
            fixture.jdbc.update("update object_status_transitions set is_active=false where code='LEAD_REFUSE_OPENED'");
            fixture.jdbc.update(
                    "update object_status_transitions set unfinished_task_action='CANCEL_ALL' where code='LEAD_REFUSE_SIGNED'");
            connection.commit();
            var originalSchema = schema(connection);
            var originalSeeds = seeds(connection);
            var originalObject = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261013_refusal_cancel_all.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select unfinished_task_action from object_status_transitions where is_active and is_refusal_entry and not is_system_only",
                                String.class))
                        .isNotEmpty()
                        .containsOnly("CANCEL_ALL");
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='IC_REFUSED'",
                                String.class))
                        .isEqualTo("NONE");
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='LEAD_REFUSE_OPENED'",
                                String.class))
                        .isEqualTo("CANCEL");
                var appliedSeeds = seeds(connection);
                var backup = query(
                        connection,
                        "select to_jsonb(b)::text from object_refusal_task_policy_backup b order by transition_id");
                update(migration);
                assertThat(seeds(connection)).isEqualTo(appliedSeeds);
                assertThat(
                                query(
                                        connection,
                                        "select to_jsonb(b)::text from object_refusal_task_policy_backup b order by transition_id"))
                        .isEqualTo(backup);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(originalObject);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(originalSchema);
                assertThat(seeds(connection)).isEqualTo(originalSeeds);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(originalObject);
                update(migration);
                fixture.jdbc.update(
                        "update object_status_transitions set unfinished_task_action='BLOCK',version=version+1 where code='MANAGER_FOUND_TO_REFUSED'");
                fixture.jdbc.update(
                        "update object_status_transitions set version=version+1 where code='MANAGER_IN_PROGRESS_TO_REFUSED'");
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='MANAGER_FOUND_TO_REFUSED'",
                                String.class))
                        .isEqualTo("BLOCK");
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='MANAGER_IN_PROGRESS_TO_REFUSED'",
                                String.class))
                        .isEqualTo("CANCEL_ALL");
                assertThat(schema(connection)).isEqualTo(originalSchema);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
