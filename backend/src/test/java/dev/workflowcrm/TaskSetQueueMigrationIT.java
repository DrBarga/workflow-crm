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
class TaskSetQueueMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void queueMigrationIsRepeatableAndRollbackRestoresOriginalSchema() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                """
                        select i.name,i.needs_review,r.code from task_template_items i
                        join roles r on r.id=i.result_recipient_role_id
                        where i.template_id='58050000-0000-4000-8000-000000000000' order by i.sort_order
                        """)) {
                    for (int index = 0; index < 4; index++) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getBoolean(2)).isEqualTo(index < 3);
                        assertThat(result.getString(3))
                                .isEqualTo(index < 3 ? "REGIONAL_MANAGER" : "DEVELOPMENT_DIRECTOR");
                    }
                    assertThat(result.next()).isFalse();
                }
                var migrated = ObjectPolicyMigrationSnapshot.schema(connection);
                update(migration);
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(migrated);
                rollbackQueue(connection, migration);
                var baseline = ObjectPolicyMigrationSnapshot.schema(connection);
                try (var statement = connection.createStatement();
                        var result = statement.executeQuery("select to_regclass('object_task_set_jobs')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isNull();
                }
                update(migration);
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(migrated);
                rollbackQueue(connection, migration);
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(baseline);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }

    private void rollbackQueue(java.sql.Connection connection, Liquibase migration) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "select count(*) from databasechangelog where orderexecuted >= (select orderexecuted from databasechangelog where id='20261021-task-set-queue')")) {
            assertThat(result.next()).isTrue();
            migration.rollback(result.getInt(1), new Contexts(), new LabelExpression());
        }
    }
}
