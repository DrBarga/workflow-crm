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
class ObjectImportMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void importTablesApplyRollbackAndReapplyWithoutChangingObjects() throws Exception {
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
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var data = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261010_object_imports.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from pg_indexes where indexname like 'ix_object_imports_%'",
                                Integer.class))
                        .isEqualTo(1);
                fixture.jdbc.update(
                        "insert into object_imports(id,actor_id,file_hash,row_count) values(gen_random_uuid(),gen_random_uuid(),repeat('a',64),0)");
                assertThat(fixture.jdbc.queryForObject("select state from object_imports", String.class))
                        .isEqualTo("QUEUED");
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(data);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_imports", Integer.class))
                        .isZero();
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(data);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
