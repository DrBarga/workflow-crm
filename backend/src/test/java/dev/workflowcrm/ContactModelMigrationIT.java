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
class ContactModelMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void preservesContactsAndLinksNormalizesPhonesAndRestoresPreviousSchema() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            update(migration);
            var fixture = new ObjectContactMigrationFixture(connection);
            int changes = fixture.jdbc.queryForObject(
                    """
                    select count(*) from databasechangelog where orderexecuted >=
                    (select orderexecuted from databasechangelog where id='20261016_contact_model' and author='bohdan')
                    """,
                    Integer.class);
            migration.rollback(changes, new Contexts(), new LabelExpression());
            fixture.seed();
            var jdbc = fixture.jdbc;
            UUID contact = UUID.randomUUID(), orphan = UUID.randomUUID(), second = UUID.randomUUID();
            jdbc.update(
                    "insert into contacts(id,name,role,phone,email) values (?, 'Власник', 'Брокер', '00 380 (67) 123-45-67', 'owner@example.com')",
                    contact);
            jdbc.update(
                    "insert into contacts(id,name,role,email) values (?, 'Юрист', 'Legal', 'legal@example.com')",
                    orphan);
            jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", fixture.object, contact);
            jdbc.update("update object_creation_requests set contact_id=? where object_id=?", contact, fixture.object);
            fixture.createObject(second, false);
            jdbc.update(
                    "insert into object_contacts(object_id,contact_id) values (?,?),(?,?)",
                    second,
                    contact,
                    second,
                    orphan);
            var schema = fixture.schema();
            var original = jdbc.queryForList("select to_jsonb(c)::text from contacts c order by id", String.class);
            var links = jdbc.queryForList(
                    "select to_jsonb(c)::text from object_contacts c order by object_id,contact_id", String.class);
            update(migration);
            assertThat(jdbc.queryForObject("select normalized_phone from contacts where id=?", String.class, contact))
                    .isEqualTo("380671234567");
            assertThat(jdbc.queryForObject("select normalized_phone from contacts where id=?", String.class, orphan))
                    .isNull();
            assertThat(jdbc.queryForObject("select author_id from contacts where id=?", UUID.class, contact))
                    .isNull();
            assertThat(jdbc.queryForObject("select author_id from contacts where id=?", UUID.class, orphan))
                    .isNull();
            assertThat(jdbc.queryForObject(
                            "select count(*) from contacts where contact_type_id is not null", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from contact_types", Integer.class))
                    .isEqualTo(4);
            var types = jdbc.queryForList("select id from contact_types order by code", UUID.class);
            update(migration);
            assertThat(jdbc.queryForList("select id from contact_types order by code", UUID.class))
                    .isEqualTo(types);
            assertThat(jdbc.queryForList(
                            "select to_jsonb(c)::text from object_contacts c order by object_id,contact_id",
                            String.class))
                    .isEqualTo(links);
            jdbc.update(
                    "update contacts set phone='+49 (30) 1234-5678', comment='Технічний контакт', contact_type_id=(select id from contact_types where code='OWNER') where id=?",
                    contact);
            assertThat(jdbc.queryForObject("select normalized_phone from contacts where id=?", String.class, contact))
                    .isEqualTo("493012345678");
            jdbc.update("update contacts set phone='00 380 (67) 123-45-67' where id=?", contact);
            ContactModelSchemaAssertions.verify(jdbc, contact, fixture.manager, second);
            migration.rollback(changes, new Contexts(), new LabelExpression());
            assertThat(fixture.schema()).isEqualTo(schema);
            assertThat(jdbc.queryForList("select to_jsonb(c)::text from contacts c order by id", String.class))
                    .isEqualTo(original);
            assertThat(jdbc.queryForList(
                            "select to_jsonb(c)::text from object_contacts c order by object_id,contact_id",
                            String.class))
                    .isEqualTo(links);
            update(migration);
            update(migration);
            assertThat(jdbc.queryForObject("select count(*) from contact_types", Integer.class))
                    .isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from contacts", Integer.class))
                    .isEqualTo(2);
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
        migration.getDatabase().getConnection().setAutoCommit(true);
    }
}
