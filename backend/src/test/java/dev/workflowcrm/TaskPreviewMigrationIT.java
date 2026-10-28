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
class TaskPreviewMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesLegacyTemplatesAndRestoresSchemaOnRollbackAndReapply() throws Exception {
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
            new TaskModelMigrationFixture(connection).seed();
        }
        for (String file : java.util.List.of("20261012_task_model.yaml", "20261019_task_workflow_fields.yaml")) {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                var db =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (var prerequisite =
                        new Liquibase("db/changelog/tasks/" + file, new ClassLoaderResourceAccessor(), db)) {
                    update(prerequisite);
                }
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var jdbc = new TaskModelMigrationFixture(connection).jdbc;
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var items =
                    jdbc.queryForList("select to_jsonb(t)::text from task_template_items t order by id", String.class);
            var tasks = jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/tasks/20261020_task_preview.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                update(migration);
                assertThat(jdbc.queryForList(
                                "select (to_jsonb(t)-'assignment_rule'-'role_id')::text from task_template_items t order by id",
                                String.class))
                        .isEqualTo(items);
                assertThat(jdbc.queryForList("select assignment_rule from task_template_items", String.class))
                        .containsOnly("EXPLICIT_USER");
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(t)::text from task_template_items t order by id", String.class))
                        .isEqualTo(items);
                update(migration);
                assertThat(jdbc.queryForObject("select count(*) from task_template_item_dependencies", Integer.class))
                        .isZero();
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
