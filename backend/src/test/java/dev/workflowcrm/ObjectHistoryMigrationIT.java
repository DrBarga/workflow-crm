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
class ObjectHistoryMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void backfillOrdersLegacyHistoryAndRollbackPreservesEntriesAndDeletionSnapshots() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261008_object_history.yaml", new ClassLoaderResourceAccessor(), db)) {
                migration.rollback(1, new Contexts(), new LabelExpression());
                fixture.seed();
                UUID early = UUID.randomUUID(), late = UUID.randomUUID(), request = UUID.randomUUID();
                for (var entry : java.util.Map.of(early, "2026-01-01", late, "2026-01-02")
                        .entrySet())
                    fixture.jdbc.update(
                            "insert into object_history(id,object_id,author_id,actor_type,field_name,new_value,object_version,created_at) values (?,?,?,'USER','name','legacy',0,?::timestamptz)",
                            entry.getKey(),
                            fixture.object,
                            fixture.manager,
                            entry.getValue());
                fixture.jdbc.update(
                        """
                    insert into entry_point_approval_requests(id,object_id,initiator_id,confirming_role_id,longitude,latitude,object_version,status,decided_by_id)
                    select ?,?,?,id,30.5,50.4,0,'REJECTED',? from roles where code='REGIONAL_MANAGER'
                    """,
                        request,
                        fixture.object,
                        fixture.manager,
                        fixture.manager);
                UUID archived = UUID.randomUUID();
                fixture.jdbc.update(
                        "insert into object_deletion_audit(object_id,actor_id,object_version,snapshot) values (?,?,0,'{}')",
                        archived,
                        fixture.manager);
                connection.commit();
                String objectBefore = fixture.objectSnapshot(fixture.object);
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select id from object_history where field_name='name' order by sequence", UUID.class))
                        .containsExactly(early, late);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_history", Integer.class))
                        .isEqualTo(4);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(objectBefore);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_history", Integer.class))
                        .isEqualTo(4);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_deletion_audit", Integer.class))
                        .isEqualTo(1);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_history", Integer.class))
                        .isEqualTo(4);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_deletion_audit", Integer.class))
                        .isEqualTo(1);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
