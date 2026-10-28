package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
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
class ObjectFileMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void backfillRollbackAndReapplyPreserveMetadataAndDeletionState() throws Exception {
        try (var connection = connection()) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
                ObjectMigrationBaseline.rollbackFrom(baseline, connection, "20261009-object-files");
            }
        }
        try (var connection = connection()) {
            connection.setAutoCommit(false);
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            UUID older = UUID.randomUUID(), newer = UUID.randomUUID();
            fixture.jdbc.update(
                    "insert into files(id,object_id,author_id,bucket,storage_key,original_name,content_type,size_bytes,created_at,deleted_at,version) values(?,?,?,'test-files','older','a.pdf','application/pdf',7,'2026-01-01',null,0),(?,?,?,'test-files','newer','b.pdf','application/pdf',8,'2026-01-02','2026-01-03',1)",
                    older,
                    fixture.object,
                    fixture.manager,
                    newer,
                    fixture.object,
                    fixture.manager);
            connection.commit();
            String before = fixture.jdbc.queryForObject(
                    "select jsonb_agg(to_jsonb(f) order by created_at)::text from files f", String.class);
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration = new Liquibase(
                    "db/changelog/files/20261009_object_files.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForList("select id from files order by sequence", UUID.class))
                        .containsExactly(older, newer);
                assertThat(fixture.jdbc.queryForObject(
                                "select jsonb_agg(to_jsonb(f)-'sequence' order by created_at)::text from files f",
                                String.class))
                        .isEqualTo(before);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select deleted_at is not null from files where id=?", Boolean.class, newer))
                        .isTrue();
                fixture.jdbc.update(
                        "insert into file_upload_reservations(id,bucket,storage_key) values(?,'test-files','pending')",
                        UUID.randomUUID());
                connection.commit();
                assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                        .isInstanceOf(liquibase.exception.LiquibaseException.class);
                connection.rollback();
                assertThat(fixture.jdbc.queryForObject("select count(*) from files", Integer.class))
                        .isEqualTo(2);
                fixture.jdbc.update("delete from file_upload_reservations");
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select jsonb_agg(to_jsonb(f) order by created_at)::text from files f", String.class))
                        .isEqualTo(before);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void update(Liquibase migration) throws Exception {
        liquibase.Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
