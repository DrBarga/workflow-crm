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
class TaskModelMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesLegacyTasksAndRestoresExactSchemaAndSeedsAfterRollback() throws Exception {
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
            var fixture = new TaskModelMigrationFixture(connection);
            fixture.seed();
            var jdbc = fixture.jdbc;
            connection.commit();
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var seeds = ObjectPolicyMigrationSnapshot.seeds(connection);
            var tasks = jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/tasks/20261012_task_model.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                var statuses = jdbc.queryForList(
                        "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class);
                var contracts =
                        jdbc.queryForList("select to_jsonb(c)::text from contract_types c order by id", String.class);
                update(migration);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class))
                        .isEqualTo(statuses)
                        .hasSize(6);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(c)::text from contract_types c order by id", String.class))
                        .isEqualTo(contracts);
                assertThat(jdbc.queryForObject(
                                "select count(*) from tasks where source_item_id is not null and task_set_id is not null",
                                Integer.class))
                        .isEqualTo(3);
                assertThat(jdbc.queryForObject("select state from task_sets", String.class))
                        .isEqualTo("ACTIVE");
                assertThat(jdbc.queryForObject(
                                "select count(*) from tasks where completed_at is not null", Integer.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForList(
                                "select (to_jsonb(t)-'task_set_id'-'template_version_id'-'source_item_id'-'completed_at')::text from tasks t order by id",
                                String.class))
                        .isEqualTo(tasks);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(seeds);
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                update(migration);
                assertThat(jdbc.queryForObject("select count(*) from task_sets", Integer.class))
                        .isEqualTo(1);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
