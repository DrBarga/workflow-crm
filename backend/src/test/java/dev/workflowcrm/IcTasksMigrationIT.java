package dev.workflowcrm;

import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.schema;
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
class IcTasksMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void taskMigrationRollsBackExactlyAndReappliesWithoutChangingReferenceSeeds() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/pre-tasks-ic-transition-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var original = schema(connection);
            var seeds = ObjectPolicyMigrationSnapshot.seeds(connection);
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration = new Liquibase(
                    "db/changelog/tasks-ic-migration-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
                migration.update(new Contexts(), new LabelExpression());
                var applied = schema(connection);
                assertThat(applied).isNotEqualTo(original);
                Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(applied);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(original);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(applied);
            }
        }
    }
}
