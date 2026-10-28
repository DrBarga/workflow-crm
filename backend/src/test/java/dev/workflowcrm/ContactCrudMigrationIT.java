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
class ContactCrudMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void appliesIdempotentlyAndRollsBackWithoutLosingContactsOrObjectLinks() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            update(migration);
            migration.rollback(2, new Contexts(), new LabelExpression());
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            var jdbc = fixture.jdbc;
            var id = UUID.randomUUID();
            jdbc.update(
                    "insert into contacts(id,name,role,phone,author_id) values (?,'Owner','Owner','+380671234567',?)",
                    id,
                    fixture.manager);
            jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", fixture.object, id);
            var schema = fixture.schema();
            var original = jdbc.queryForList("select to_jsonb(c)::text from contacts c", String.class);
            update(migration);
            update(migration);
            assertThat(jdbc.queryForObject("select deleted_at from contacts where id=?", Object.class, id))
                    .isNull();
            jdbc.update(
                    "insert into contact_history(contact_id,actor_id,contact_version,event_type,changed_fields,linked_object_ids) "
                            + "values (?,?,0,'CREATED','[\"name\"]',?::jsonb)",
                    id,
                    fixture.manager,
                    "[\"" + fixture.object + "\"]");
            assertThatThrownBy(() -> jdbc.update("delete from contacts where id=?", id))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(
                            "insert into contact_history(contact_id,actor_id,contact_version,event_type,changed_fields,linked_object_ids) "
                                    + "values (?,?,1,'UPDATED','[\"privatePhone\"]','[]')",
                            id,
                            fixture.manager))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            migration.rollback(2, new Contexts(), new LabelExpression());
            assertThat(fixture.schema()).isEqualTo(schema);
            assertThat(jdbc.queryForList("select to_jsonb(c)::text from contacts c", String.class))
                    .isEqualTo(original);
            assertThat(jdbc.queryForObject(
                            "select count(*) from object_contacts where contact_id=?", Integer.class, id))
                    .isEqualTo(1);
            update(migration);
            assertThat(jdbc.queryForObject("select count(*) from contact_history", Integer.class))
                    .isZero();
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        ContactMigrationCheckpoint.update(migration);
        migration.getDatabase().getConnection().setAutoCommit(true);
    }
}
