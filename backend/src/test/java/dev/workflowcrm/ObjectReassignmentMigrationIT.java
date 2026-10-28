package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

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
class ObjectReassignmentMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationRepeatsRollsBackAndPreservesExistingTaskData() throws Exception {
        try (var c =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (var baseline =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var c =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            var fixture = new ObjectContactMigrationFixture(c);
            fixture.seed();
            UUID task = UUID.randomUUID();
            fixture.jdbc.update(
                    "insert into tasks(id,object_id,source_status_id,assignee_id,name,deadline,author_id) select ?,?,id,?,'Migration task','2026-10-15',? from object_statuses where code='FOUND'",
                    task,
                    fixture.object,
                    fixture.manager,
                    fixture.manager);
            c.commit();
            try (var migration = new Liquibase(
                    "db/changelog/tasks/20261007_task_reassignment.yaml", new ClassLoaderResourceAccessor(), db)) {
                migration.rollback(1, new Contexts(), new LabelExpression());
                var schema = ObjectPolicyMigrationSnapshot.schema(c);
                String before = fixture.jdbc.queryForObject(
                        "select to_jsonb(t)::text from tasks t where id=?", String.class, task);
                String object = fixture.objectSnapshot(fixture.object);
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select transferred_from_predecessor from tasks where id=?", Boolean.class, task))
                        .isFalse();
                assertThat(fixture.jdbc.queryForObject(
                                "select (to_jsonb(t)-'transferred_from_predecessor')::text from tasks t where id=?",
                                String.class,
                                task))
                        .isEqualTo(before);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(ObjectPolicyMigrationSnapshot.schema(c)).isEqualTo(schema);
                assertThat(fixture.jdbc.queryForObject(
                                "select to_jsonb(t)::text from tasks t where id=?", String.class, task))
                        .isEqualTo(before);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from task_assignment_history", Integer.class))
                        .isZero();
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
