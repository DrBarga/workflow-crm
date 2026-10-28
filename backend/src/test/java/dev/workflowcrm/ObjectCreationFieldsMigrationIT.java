package dev.workflowcrm;

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
class ObjectCreationFieldsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void widensNameWithoutChangingExistingDataAndSafelyRollsBackAndReapplies() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/object-fields-migration-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            var before = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/object-creation-fields-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(migration);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(before);
                assertThat(length(fixture)).isEqualTo(500);
                var changesets = fixture.jdbc.queryForObject("select count(*) from databasechangelog", Integer.class);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
                        .isEqualTo(changesets);
                fixture.jdbc.update("update objects set name=repeat('а',500) where id=?", fixture.object);
                connection.commit();
                assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                        .hasMessageContaining("rollback");
                assertThat(fixture.jdbc.queryForObject(
                                "select name from objects where id=?", String.class, fixture.object))
                        .hasSize(500);
                assertThat(length(fixture)).isEqualTo(500);
                assertThat(fixture.jdbc.queryForObject(
                                "select duplicate_name from objects where id=?", String.class, fixture.object))
                        .hasSize(500);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from pg_indexes where indexname='ix_objects_duplicate_name'",
                                Integer.class))
                        .isEqualTo(1);
                fixture.jdbc.update("update objects set name='Historical' where id=?", fixture.object);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(length(fixture)).isEqualTo(250);
                update(migration);
                assertThat(length(fixture)).isEqualTo(500);
                assertThat(fixture.jdbc.queryForObject("select count(*) from contract_types", Integer.class))
                        .isEqualTo(2);
            }
        }
    }

    private int length(ObjectContactMigrationFixture fixture) {
        return fixture.jdbc.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name='objects' and column_name='name'",
                Integer.class);
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
