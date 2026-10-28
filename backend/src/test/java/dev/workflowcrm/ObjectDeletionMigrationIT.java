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
class ObjectDeletionMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void rollbackRejectsPendingWorkAndReapplyPreservesCompletedOperationsAndAudit() throws Exception {
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
            fixture.seed();
            UUID admin = UUID.randomUUID(), operation = UUID.randomUUID();
            fixture.jdbc.update(
                    "insert into users(id,email,password_hash,full_name,role_id,status) select ?,?,'test','Admin',id,'ACTIVE' from roles where code='SUPERADMIN'",
                    admin,
                    admin + "@example.com");
            fixture.jdbc.update(
                    "insert into object_deletion_operations(id,object_id,actor_id,actor_name) values(?,?,?,'Admin')",
                    operation,
                    fixture.object,
                    admin);
            connection.commit();
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261008_object_deletion.yaml", new ClassLoaderResourceAccessor(), db)) {
                assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                        .isInstanceOf(liquibase.exception.LiquibaseException.class);
                connection.rollback();
                assertThat(fixture.jdbc.queryForObject("select count(*) from objects", Integer.class))
                        .isEqualTo(1);
                fixture.jdbc.query(
                        "select set_config('workflow.object_deletion_actor',?,true),set_config('workflow.object_deletion_operation',?,true)",
                        rs -> {},
                        admin.toString(),
                        operation.toString());
                fixture.jdbc.update("delete from objects where id=?", fixture.object);
                fixture.jdbc.update(
                        "update object_deletion_operations set state='COMPLETED',completed_at=clock_timestamp() where id=?",
                        operation);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_deletion_audit", Integer.class))
                        .isEqualTo(1);
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from object_deletion_operations", Integer.class))
                        .isEqualTo(1);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_creation_requests", Integer.class))
                        .isEqualTo(1);
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_deletion_audit", Integer.class))
                        .isEqualTo(1);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_creation_requests", Integer.class))
                        .isEqualTo(1);
                assertThatThrownBy(() -> fixture.jdbc.execute("delete from object_deletion_audit"))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
