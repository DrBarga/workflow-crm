package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class TaskModelRevisionsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesDataChecksRollbackAndRejectsConflictingHistoricalCodes() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var master =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(master);
                master.rollback(1, new Contexts(), new LabelExpression());
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new TaskModelMigrationFixture(connection);
            fixture.seed();
            var jdbc = fixture.jdbc;
            jdbc.update("update tasks set status='PAUSED' where status='NEW'");
            UUID template = jdbc.queryForObject(
                    "select template_id from task_template_items where assignee_id=?", UUID.class, fixture.manager);
            UUID version = UUID.randomUUID();
            jdbc.update(
                    "insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id) select ?,id,1,repeat('a',64),trigger_status_id from task_templates where id=?",
                    version,
                    template);
            jdbc.update(
                    "insert into template_items(template_version_id,source_item_id,name,code,deadline_rule,deadline_offset_days,assignment_rule,assignee_id,sort_order) select ?,id,name,code,'FROM_START_CALENDAR',1,'EXPLICIT_USER',assignee_id,sort_order from task_template_items where template_id=?",
                    version,
                    template);
            connection.commit();
            var schema = ObjectPolicyMigrationSnapshot.schema(connection);
            var tasks = jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            var codes = jdbc.queryForList("select code from template_items order by id", String.class);
            var statuses =
                    jdbc.queryForList("select to_jsonb(s)::text from task_statuses s order by code", String.class);
            var contracts =
                    jdbc.queryForList("select to_jsonb(c)::text from contract_types c order by code", String.class);
            try (var migration = new Liquibase(
                    "db/changelog/tasks/20261024_task_model_revisions.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                update(migration);
                assertThat(jdbc.queryForList(
                                "select (to_jsonb(t)-'manually_paused')::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                assertThat(jdbc.queryForObject("select count(*) from tasks where manually_paused", Integer.class))
                        .isZero();
                assertThat(jdbc.queryForList("select code from template_items order by id", String.class))
                        .isEqualTo(codes);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(s)::text from task_statuses s order by code", String.class))
                        .isEqualTo(statuses)
                        .hasSize(6);
                assertThat(jdbc.queryForList(
                                "select to_jsonb(c)::text from contract_types c order by code", String.class))
                        .isEqualTo(contracts);
                jdbc.update("update tasks set manually_paused=true where status='PAUSED'");
                connection.commit();
                assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                        .hasStackTraceContaining("manually paused tasks still exist");
                connection.rollback();
                jdbc.update("update tasks set manually_paused=false");
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                assertThat(jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(tasks);
                assertThat(jdbc.queryForObject(
                                "select count(*) from pg_proc where proname='preserve_task_element_code'",
                                Integer.class))
                        .isZero();
                jdbc.update("update template_items set code='INCONSISTENT_HISTORY'");
                connection.commit();
                assertThatThrownBy(() -> update(migration)).hasStackTraceContaining("codes differ between versions");
                connection.rollback();
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(schema);
                jdbc.update("update template_items set code=?", codes.getFirst());
                connection.commit();
                update(migration);
                assertThat(jdbc.queryForObject(
                                "select count(*) from databasechangelog where id='20261024-task-model-revisions'",
                                Integer.class))
                        .isEqualTo(1);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
