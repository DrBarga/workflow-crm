package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

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
class IcTaskPolicyMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void changesOnlyIcTaskActionsAndPreservesDataThroughRollbackAndReapply() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline = new Liquibase(
                    "db/changelog/object-creation-fields-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            fixture.jdbc.update(
                    "insert into tasks(object_id,source_status_id,name,assignee_id,deadline,status,author_id) "
                            + "select ?,id,'Historical task',?,'2026-10-04','IN_PROGRESS',? from object_statuses where code='IC_PREPARATION'",
                    fixture.object,
                    fixture.manager,
                    fixture.manager);
            fixture.jdbc.update(
                    "update objects set investment_committee_decision='REFUSED', investment_committee_date='2026-10-01', "
                            + "status_before_refusal_id=status_id, status_id=(select id from object_statuses where code='REFUSED'), "
                            + "refusal_reason_id=(select id from refusal_reasons where code='IC_NOT_APPROVED') where id=?",
                    fixture.object);
            connection.commit();
            var beforeObject = fixture.objectSnapshot(fixture.object);
            var beforeTasks =
                    fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class);
            var beforeSeeds = ObjectPolicyMigrationSnapshot.seeds(connection);
            var beforeSchema = ObjectPolicyMigrationSnapshot.schema(connection);
            var expectedSeeds = beforeSeeds.stream()
                    .map(row -> isIcRule(row)
                            ? row.replace(
                                    "\"unfinished_task_action\": \"CANCEL\"", "\"unfinished_task_action\": \"NONE\"")
                            : row)
                    .sorted()
                    .toList();
            try (var migration = new Liquibase(
                    "db/changelog/ic-task-policy-test.yaml", new ClassLoaderResourceAccessor(), database)) {
                update(migration);
                assertThat(actions(fixture)).containsExactly("NONE", "NONE", "NONE");
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection).stream()
                                .sorted()
                                .toList())
                        .isEqualTo(expectedSeeds);
                var changesets = fixture.jdbc.queryForObject("select count(*) from databasechangelog", Integer.class);
                var appliedRules = fixture.jdbc.queryForList(
                        "select to_jsonb(t)::text from object_status_transitions t order by id", String.class);
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select to_jsonb(t)::text from object_status_transitions t order by id", String.class))
                        .isEqualTo(appliedRules);
                assertThat(fixture.jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
                        .isEqualTo(changesets);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(actions(fixture)).containsExactly("CANCEL", "CANCEL", "CANCEL");
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection)).isEqualTo(beforeSeeds);
                assertThat(ObjectPolicyMigrationSnapshot.schema(connection)).isEqualTo(beforeSchema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(beforeObject);
                assertThat(fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(beforeTasks);
                update(migration);
                assertThat(ObjectPolicyMigrationSnapshot.seeds(connection).stream()
                                .sorted()
                                .toList())
                        .isEqualTo(expectedSeeds);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(beforeObject);
                assertThat(fixture.jdbc.queryForList("select to_jsonb(t)::text from tasks t order by id", String.class))
                        .isEqualTo(beforeTasks);
            }
        }
    }

    private List<String> actions(ObjectContactMigrationFixture fixture) {
        return fixture.jdbc.queryForList(
                "select unfinished_task_action from object_status_transitions "
                        + "where code in ('IC_APPROVED','IC_APPROVED_WITH_CONDITIONS','IC_REFUSED') order by code",
                String.class);
    }

    private boolean isIcRule(String row) {
        return row.contains("\"code\": \"IC_APPROVED\"")
                || row.contains("\"code\": \"IC_APPROVED_WITH_CONDITIONS\"")
                || row.contains("\"code\": \"IC_REFUSED\"");
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
