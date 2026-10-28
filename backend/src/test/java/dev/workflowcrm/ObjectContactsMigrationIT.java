package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.UUID;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ObjectContactsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesLegacyContactsPreservesDataAndRestoresSchemaOnRollback() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var baseline = new Liquibase(
                        "db/changelog/pre-object-contacts-test.yaml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            baseline.update(new Contexts(), new LabelExpression());
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            UUID noContact = UUID.randomUUID();
            fixture.createObject(noContact, false);
            var schema = fixture.schema();
            var original = fixture.objectSnapshot(fixture.object);
            var noContactSnapshot = fixture.objectSnapshot(noContact);
            try (var migration = new Liquibase(
                    "db/changelog/object-contacts-migration-test.yaml",
                    new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
                update(migration);
                var jdbc = fixture.jdbc;
                UUID contact = jdbc.queryForObject("select id from contacts", UUID.class);
                assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject(
                                "select contact_id from object_creation_requests where object_id=?",
                                UUID.class,
                                fixture.object))
                        .isEqualTo(contact);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(original);
                assertThat(fixture.objectSnapshot(noContact)).isEqualTo(noContactSnapshot);
                update(migration);
                assertThat(jdbc.queryForObject("select id from contacts", UUID.class))
                        .isEqualTo(contact);
                assertThat(jdbc.queryForObject("select count(*) from object_contacts", Integer.class))
                        .isEqualTo(1);
                assertThatThrownBy(() -> jdbc.update(
                                "insert into object_contacts(object_id,contact_id) values (?,?)",
                                fixture.object,
                                contact))
                        .isInstanceOf(DataAccessException.class);
                assertThatThrownBy(() -> jdbc.update(
                                "insert into object_contacts(object_id,contact_id) values (?,?)",
                                fixture.object,
                                UUID.randomUUID()))
                        .isInstanceOf(DataAccessException.class);
                assertThatThrownBy(() -> jdbc.update("insert into contacts(name,role) values ('Owner','Owner')"))
                        .isInstanceOf(DataAccessException.class);
                UUID referenced = UUID.randomUUID();
                fixture.createObject(referenced, false);
                fixture.request(referenced);
                jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", referenced, contact);
                jdbc.update("update object_creation_requests set contact_id=? where object_id=?", contact, referenced);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(fixture.schema()).isEqualTo(schema);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(original);
                assertThat(fixture.objectSnapshot(noContact)).isEqualTo(noContactSnapshot);
                assertThat(jdbc.queryForObject(
                                "select contact_phone from objects where id=?", String.class, referenced))
                        .isEqualTo("+380671234567");
                assertThat(jdbc.queryForObject(
                                "select request_hash from object_creation_requests where object_id=?",
                                String.class,
                                fixture.object))
                        .isEqualTo("a".repeat(64));
                update(migration);
                update(migration);
                assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject("select count(*) from object_contacts", Integer.class))
                        .isEqualTo(2);
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
        migration.getDatabase().getConnection().setAutoCommit(true);
    }
}
