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
class ObjectFieldMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesHistoricalValuesRestoresSchemaAndReferencesAndReappliesWithoutDuplicates() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/tasks-ic-migration-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            fixture.jdbc.update(
                    "update objects set object_type_id=(select id from object_types where code='RESIDENTIAL_GROUND_FLOOR'), contract_type='Основний',investment_committee_decision='APPROVED_WITH_CONDITIONS',investment_committee_date='2026-10-01',investment_committee_conditions=repeat('а',100),contract_signed_date='2026-10-02',opening_date='2026-10-03' where id=?",
                    fixture.object);
            fixture.jdbc.update("update object_statuses set is_task_generation_trigger=true where code='FOUND'");
            var originalObject = fixture.objectSnapshot(fixture.object);
            var originalSchema = schema(connection);
            var originalSeeds = seeds(connection);
            try (var migration = new Liquibase(
                    "db/changelog/object-fields-migration-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select legacy_investment_committee_conditions from objects where id=?",
                                String.class,
                                fixture.object))
                        .hasSize(100);
                assertThat(fixture.jdbc.queryForObject(
                                "select investment_committee_conditions from objects where id=?",
                                String.class,
                                fixture.object))
                        .isNull();
                assertThat(fixture.jdbc.queryForObject(
                                "select t.code from objects o join contract_types t on t.id=o.contract_type_id where o.id=?",
                                String.class,
                                fixture.object))
                        .isEqualTo("PRIMARY");
                assertThat(fixture.jdbc.queryForObject(
                                "select t.code from objects o join object_types t on t.id=o.object_type_id where o.id=?",
                                String.class,
                                fixture.object))
                        .isEqualTo("RESIDENTIAL_GROUND_FLOOR");
                var appliedSchema = schema(connection);
                var appliedSeeds = seeds(connection);
                var contractIds = query(connection, "select id::text from contract_types order by code");
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(appliedSchema);
                assertThat(seeds(connection)).isEqualTo(appliedSeeds);
                assertThat(query(connection, "select id::text from contract_types order by code"))
                        .isEqualTo(contractIds);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).containsExactlyInAnyOrderElementsOf(originalSchema);
                assertThat(seeds(connection)).isEqualTo(originalSeeds);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(originalObject);
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject("select count(*) from contract_types", Integer.class))
                        .isEqualTo(2);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from object_types where is_active", Integer.class))
                        .isEqualTo(4);
            }
        }
    }

    private void clearCache() {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
    }
}
