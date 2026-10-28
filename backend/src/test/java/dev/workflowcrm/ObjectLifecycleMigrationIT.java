package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.util.List;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ObjectLifecycleMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void updatesExistingDataWithoutLosingOriginalValuesAndRestoresSchema() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/object-list-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        java.util.UUID object;
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var fixture = new TaskModelMigrationFixture(connection);
            fixture.seed();
            object = fixture.object;
            fixture.jdbc.update(
                    "update objects set opening_planned_date='2026-12-01',construction_acceptance_planned_date='2026-10-23',construction_term_days=2 where id=?",
                    object);
            fixture.jdbc.update("update task_template_items set name='Погодити договір в системі Lotus'");
        }
        for (String path : List.of(
                "20261012_task_model.yaml", "20261019_task_workflow_fields.yaml", "20261020_task_preview.yaml")) {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                var db =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (var baseline =
                        new Liquibase("db/changelog/tasks/" + path, new ClassLoaderResourceAccessor(), db)) {
                    update(baseline);
                }
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            var jdbc = fixture.jdbc;
            var schema = fixture.schema();
            var original = fixture.objectSnapshot(object);
            var tasks = jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            var policy = jdbc.queryForList(
                    "select to_jsonb(t)::text from object_status_transitions t order by id", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261022_object_lifecycle.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                update(migration);
                assertThat(jdbc.queryForObject(
                                "select opening_planned_date::text from objects where id=?", String.class, object))
                        .isEqualTo("2026-10-25");
                assertThat(jdbc.queryForList("select code from task_template_items", String.class))
                        .containsOnly("APPROVE_LOTUS_CONTRACT");
                assertThat(jdbc.queryForList("select code from template_items", String.class))
                        .containsOnly("APPROVE_LOTUS_CONTRACT");
                assertThat(jdbc.queryForObject("select count(*) from object_transition_required_tasks", Integer.class))
                        .isEqualTo(3);
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                jdbc.update("update objects set opening_planned_date='2030-01-01' where id=?", object);
                assertThat(jdbc.queryForObject(
                                "select opening_planned_date::text from objects where id=?", String.class, object))
                        .isEqualTo("2026-10-25");
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.schema()).isEqualTo(schema);
                assertThat(fixture.objectSnapshot(object)).isEqualTo(original);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(t)::text from object_status_transitions t order by id", String.class))
                        .isEqualTo(policy);
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                update(migration);
                jdbc.update("update objects set short_comment='Новий коментар' where id=?", object);
                connection.commit();
                assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                        .hasMessageContaining("Cannot roll back object lifecycle");
                connection.rollback();
                jdbc.update("update objects set short_comment=null where id=?", object);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.schema()).isEqualTo(schema);
                update(migration);
                assertThat(jdbc.queryForObject("select count(*) from object_transition_required_tasks", Integer.class))
                        .isEqualTo(3);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
