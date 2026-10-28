package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ObjectCommentMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void legacyBackfillRollbackAndReapplyKeepTextAuthorsAndChronology() throws Exception {
        try (var connection = connection()) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                ObjectMigrationBaseline.rollbackFrom(migration, connection, "20261008-object-comments");
            }
        }
        try (var connection = connection()) {
            connection.setAutoCommit(false);
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            UUID older = UUID.randomUUID(), newer = UUID.randomUUID();
            fixture.jdbc.update(
                    "insert into object_comments(id,object_id,author_id,body,created_at,updated_at,version) values(?,?,?,'old','2026-01-01T00:00:00Z','2026-01-02T00:00:00Z',2),(?,?,?,'new','2026-01-03T00:00:00Z','2026-01-03T00:00:00Z',0)",
                    older,
                    fixture.object,
                    fixture.manager,
                    newer,
                    fixture.object,
                    fixture.manager);
            connection.commit();
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261008_object_comments.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForList("select id from object_comments order by sequence", UUID.class))
                        .containsExactly(older, newer);
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, older))
                        .isEqualTo(2);
                assertThat(fixture.jdbc.queryForObject(
                                "select edited_at=updated_at from object_comments where id=?", Boolean.class, older))
                        .isTrue();
                assertThat(fixture.jdbc.queryForObject(
                                "select edited_at is null from object_comments where id=?", Boolean.class, newer))
                        .isTrue();
                assertThatThrownBy(() -> fixture.jdbc.update(
                                "update object_comments set author_id=? where id=?", UUID.randomUUID(), older))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                connection.rollback();
                fixture.jdbc.update("update object_comments set body='changed' where id=?", newer);
                connection.commit();
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, newer))
                        .isEqualTo(1);
                migration.rollback(1, new Contexts(), new LabelExpression());
                update(migration);
                update(migration);
                assertThat(fixture.jdbc.queryForList(
                                "select body from object_comments order by sequence", String.class))
                        .containsExactly("old", "changed");
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from object_comments where edited_at is not null", Integer.class))
                        .isEqualTo(2);
                UUID appended = UUID.randomUUID();
                fixture.jdbc.update(
                        "insert into object_comments(id,object_id,author_id,body) values(?,?,?,'after')",
                        appended,
                        fixture.object,
                        fixture.manager);
                assertThat(fixture.jdbc.queryForObject(
                                "select sequence from object_comments where id=?", Long.class, appended))
                        .isGreaterThan(2);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                UUID removing = UUID.randomUUID(), admin = UUID.randomUUID(), operation = UUID.randomUUID();
                fixture.createObject(removing, false);
                fixture.jdbc.update(
                        "insert into users(id,email,password_hash,full_name,role_id,status) select ?,?,'test','Admin',id,'ACTIVE' from roles where code='SUPERADMIN'",
                        admin,
                        admin + "@example.com");
                fixture.jdbc.update(
                        "insert into object_deletion_operations(id,object_id,actor_id,actor_name) values(?,?,?,'Admin')",
                        operation,
                        removing,
                        admin);
                connection.commit();
                assertThatThrownBy(() -> update(migration)).isInstanceOf(liquibase.exception.LiquibaseException.class);
                connection.rollback();
                assertThat(fixture.jdbc.queryForObject(
                                "select count(*) from information_schema.columns where table_name='object_comments' and column_name='sequence'",
                                Integer.class))
                        .isZero();
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_comments", Integer.class))
                        .isEqualTo(3);
                fixture.jdbc.query(
                        "select set_config('workflow.object_deletion_actor',?,true),set_config('workflow.object_deletion_operation',?,true)",
                        rs -> {},
                        admin.toString(),
                        operation.toString());
                fixture.jdbc.update("delete from objects where id=?", removing);
                fixture.jdbc.update(
                        "update object_deletion_operations set state='COMPLETED',completed_at=clock_timestamp() where id=?",
                        operation);
                connection.commit();
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from object_comments", Integer.class))
                        .isEqualTo(3);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
