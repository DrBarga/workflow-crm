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
class ContactAuditMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradeAndRollbackPreserveExistingAuditMetadataAndRejectUnknownFields() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            update(migration);
            migration.rollback(1, new Contexts(), new LabelExpression());
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            var jdbc = fixture.jdbc;
            var id = UUID.randomUUID();
            jdbc.update(
                    "insert into contacts(id,name,role,email,author_id) values (?,'Owner','Owner','a@example.com',?)",
                    id,
                    fixture.manager);
            jdbc.update(
                    "insert into contact_history(contact_id,actor_id,contact_version,event_type,changed_fields,linked_object_ids) "
                            + "values (?,?,0,'CREATED','[\"name\"]','[]')",
                    id,
                    fixture.manager);
            var original = jdbc.queryForList("select to_jsonb(h)::text from contact_history h", String.class);
            update(migration);
            update(migration);
            assertThat(jdbc.queryForObject("select changed_values::text from contact_history", String.class))
                    .isEqualTo("{}");
            assertThatThrownBy(() -> jdbc.update("update contact_history set changed_values='[]'"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update contact_history set changed_values='{\"password\":{}}'"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update("update contact_history set changed_values='{\"name\":{\"before\":null,\"after\":\"Owner\"}}'");
            migration.rollback(1, new Contexts(), new LabelExpression());
            assertThat(jdbc.queryForList("select to_jsonb(h)::text from contact_history h", String.class))
                    .isEqualTo(original);
            assertThat(jdbc.queryForObject("select count(*) from contacts where id=?", Integer.class, id))
                    .isEqualTo(1);
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        ContactMigrationCheckpoint.update(migration);
        migration.getDatabase().getConnection().setAutoCommit(true);
    }
}
