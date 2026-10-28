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
class ObjectListMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void indexesApplyRepeatRollbackAndReapplyWithoutChangingData() throws Exception {
        try (var c =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (var baseline =
                    new Liquibase("db/changelog/object-refusal-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var c =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            var fixture = new ObjectContactMigrationFixture(c);
            fixture.seed();
            c.commit();
            var schema = ObjectPolicyMigrationSnapshot.schema(c);
            var data = fixture.objectSnapshot(fixture.object);
            try (var migration =
                    new Liquibase("db/changelog/object-list-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from pg_indexes where indexname like 'ix_objects_list_%'",
                                Integer.class))
                        .isEqualTo(10);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(c)).isEqualTo(schema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(data);
                update(migration);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(data);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
