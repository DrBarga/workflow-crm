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
class ObjectCommentMentionsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void legacyCommentsRollbackReapplyAndMentionOnlyEditsPreserveMetadataRules() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var master =
                    new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(master);
                ObjectMigrationBaseline.rollbackFrom(master, connection, "20261015-object-comment-mentions", "bohdan");
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            UUID comment = UUID.randomUUID();
            fixture.jdbc.update(
                    "insert into object_comments(id,object_id,author_id,body) values(?,?,?,'@Migration manager')",
                    comment,
                    fixture.object,
                    fixture.manager);
            var schema = fixture.schema();
            String before = fixture.jdbc.queryForObject(
                    "select to_jsonb(c)::text from object_comments c where id=?", String.class, comment);
            String originalFunction = fixture.jdbc.queryForObject(
                    "select pg_get_functiondef('maintain_object_comment()'::regprocedure)", String.class);
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var migration = new Liquibase(
                    "db/changelog/objects/20261015_object_comment_mentions.yaml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select (to_jsonb(c)-'mentions')::text from object_comments c where id=?",
                                String.class,
                                comment))
                        .isEqualTo(before);
                assertThat(fixture.jdbc.queryForObject(
                                "select mentions::text from object_comments where id=?", String.class, comment))
                        .isEqualTo("[]");
                update(migration);
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, comment))
                        .isZero();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.schema()).isEqualTo(schema);
                assertThat(fixture.jdbc.queryForObject(
                                "select to_jsonb(c)::text from object_comments c where id=?", String.class, comment))
                        .isEqualTo(before);
                assertThat(fixture.jdbc
                                .queryForObject(
                                        "select pg_get_functiondef('maintain_object_comment()'::regprocedure)",
                                        String.class)
                                .replace("\r\n", "\n"))
                        .isEqualTo(originalFunction.replace("\r\n", "\n"));
                update(migration);
                String refs = "[{\"userId\":\"" + fixture.manager + "\",\"offset\":0,\"length\":18}]";
                fixture.jdbc.update("update object_comments set mentions=?::jsonb where id=?", refs, comment);
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, comment))
                        .isEqualTo(1);
                assertThat(fixture.jdbc.queryForObject(
                                "select edited_at is not null and edited_at=updated_at from object_comments where id=?",
                                Boolean.class,
                                comment))
                        .isTrue();
                fixture.jdbc.update("update object_comments set mentions=?::jsonb where id=?", refs, comment);
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, comment))
                        .isEqualTo(1);
                connection.commit();
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, comment))
                        .isEqualTo(1);
                fixture.jdbc.update("update object_comments set body='Updated text' where id=?", comment);
                assertThat(fixture.jdbc.queryForObject(
                                "select version from object_comments where id=?", Long.class, comment))
                        .isEqualTo(2);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
