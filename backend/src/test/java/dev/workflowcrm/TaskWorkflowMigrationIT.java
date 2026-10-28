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
class TaskWorkflowMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesExistingTasksAndRestoresExactSchemaOnRollbackAndReapply() throws Exception {
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
            connection.commit();
            try (var model = new Liquibase(
                    "db/changelog/tasks/20261012_task_model.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(model);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var jdbc = new TaskModelMigrationFixture(connection).jdbc;
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var tasks = jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            var statuses = jdbc.queryForList(
                    "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class);
            var contracts =
                    jdbc.queryForList("select to_jsonb(c)::text from contract_types c order by id", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/tasks/20261019_task_workflow_fields.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(jdbc.queryForObject(
                                "select count(*) from tasks where author_type='SYSTEM' and needs_review and result_recipient_id is null",
                                Integer.class))
                        .isEqualTo(3);
                assertThat(jdbc.queryForList(
                                "select (to_jsonb(t)-'author_type'-'needs_review'-'result_recipient_id')::text from tasks t order by id",
                                String.class))
                        .isEqualTo(tasks);
                var migratedStatuses = jdbc.queryForList(
                        "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class);
                update(migration);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class))
                        .isEqualTo(migratedStatuses)
                        .hasSize(6);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(c)::text from contract_types c order by id", String.class))
                        .isEqualTo(contracts);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(s)::text from task_statuses s order by sort_order", String.class))
                        .isEqualTo(statuses);
                update(migration);
                assertThat(jdbc.queryForObject("select name from task_statuses where code='DONE'", String.class))
                        .isEqualTo("Завершена");
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
