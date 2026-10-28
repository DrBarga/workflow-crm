package dev.workflowcrm;

import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.*;
import static org.assertj.core.api.Assertions.*;

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
class ObjectModelAdditionsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesDataRepeatsWithoutDuplicateSeedsAndRestoresOriginalSchema() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/object-list-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            connection.commit();
            var originalSchema = schema(connection);
            var originalSeeds = seeds(connection);
            var originalObject = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261013_object_model_additions.yaml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select data_source_id from objects where id=?", String.class, fixture.object))
                        .isNull();
                var sources = query(connection, "select to_jsonb(s)::text from object_data_sources s order by code");
                assertThat(sources).hasSize(5);
                update(migration);
                assertThat(query(connection, "select to_jsonb(s)::text from object_data_sources s order by code"))
                        .isEqualTo(sources);
                assertThat(seeds(connection)).isEqualTo(originalSeeds);
                assertThat(fixture.jdbc.queryForObject(
                                "select (to_jsonb(o)-'data_source_id')::text from objects o where id=?",
                                String.class,
                                fixture.object))
                        .isEqualTo(originalObject);
                fixture.jdbc.update(
                        "update objects set data_source_id=(select id from object_data_sources where code='BROKER')");
                connection.commit();
                migration.rollback(2, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(originalSchema);
                assertThat(seeds(connection)).isEqualTo(originalSeeds);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(originalObject);
                update(migration);
                fixture.jdbc.update(
                        "update object_status_transitions set unfinished_task_action='CANCEL_ALL' where code='MANAGER_FOUND_TO_IN_PROGRESS'");
                connection.commit();
                migration.rollback(2, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select unfinished_task_action from object_status_transitions where code='MANAGER_FOUND_TO_IN_PROGRESS'",
                                String.class))
                        .isEqualTo("CANCEL");
                assertThat(schema(connection)).isEqualTo(originalSchema);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_data_sources", Integer.class))
                        .isEqualTo(5);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
