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
class ObjectRefusalMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void rollbackAndReapplyPreserveExistingRefusalDataAndConfiguration() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/object-status-execution-test.yaml", new ClassLoaderResourceAccessor(), database)) {
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
                    "update objects set status_id=(select id from object_statuses where code='REFUSED'), refusal_reason_id=(select id from refusal_reasons where code='LOW_POTENTIAL') where id=?",
                    fixture.object);
            connection.commit();
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var seeds = ObjectPolicyMigrationSnapshot.seeds(connection);
            var object = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/object-refusal-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from object_transition_validation_roles", Integer.class))
                        .isEqualTo(5);
                assertThat(fixture.jdbc.queryForObject(
                                "select code from object_refusal_tags where object_id=?", String.class, fixture.object))
                        .isEqualTo("LOW_POTENTIAL");
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from object_transition_validation_roles", Integer.class))
                        .isEqualTo(5);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                update(migration);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_refusal_tags", Integer.class))
                        .isEqualTo(1);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
