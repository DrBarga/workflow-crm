package dev.workflowcrm;

import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.query;
import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.schema;
import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.seeds;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ObjectTransitionPolicyRollbackIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesExistingDatabaseRollsBackExactlyAndReappliesWithoutDuplicates() throws Exception {
        try (var connection = connection();
                var baseline = migration(connection, "db/changelog/pre-object-transition-policy-test.yaml")) {
            baseline.update(new Contexts(), new LabelExpression());
        }
        try (var connection = connection()) {
            connection
                    .createStatement()
                    .execute(
                            """
                    insert into regions(code,name) values ('POLICY_TEST','Existing region');
                    insert into users(email,password_hash,full_name,role_id,status)
                        select 'policy@example.com','test-hash','Existing user',id,'ACTIVE' from roles where code='MANAGER';
                    insert into objects(name,region_id,manager_id,author_id,status_id,entrance_longitude,entrance_latitude,object_area)
                        select 'Existing object',r.id,u.id,u.id,s.id,30.5,50.4,100 from regions r,users u,object_statuses s
                        where r.code='POLICY_TEST' and u.email='policy@example.com' and s.code='FOUND'
                    """);
            var originalSchema = schema(connection);
            var originalSeeds = seeds(connection);
            var originalObjects = query(connection, "select to_jsonb(o)::text from objects o order by id");
            try (var migration = migration(connection, "db/changelog/pre-object-duplicates-test.yaml")) {
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isNotEqualTo(originalSchema);
                assertThat(query(connection, "select count(*)::text from object_status_transitions"))
                        .containsExactly("56");
                var upgradedSeeds = seeds(connection);
                var changelog = query(connection, "select id from databasechangelog order by orderexecuted");
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(seeds(connection)).containsExactlyElementsOf(upgradedSeeds);
                assertThat(query(connection, "select id from databasechangelog order by orderexecuted"))
                        .containsExactlyElementsOf(changelog);
                migration.rollback(2, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).containsExactlyElementsOf(originalSchema);
                assertThat(seeds(connection)).containsExactlyElementsOf(originalSeeds);
                assertThat(query(connection, "select to_jsonb(o)::text from objects o order by id"))
                        .containsExactlyElementsOf(originalObjects);
                clearCache();
                migration.update(new Contexts(), new LabelExpression());
                assertThat(query(connection, "select count(*)::text from object_status_transitions"))
                        .containsExactly("56");
                assertThat(query(
                                connection,
                                "select count(*)::text from object_statuses where is_task_generation_trigger"))
                        .containsExactly("5");
            }
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Liquibase migration(Connection connection, String root) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        return new Liquibase(root, new ClassLoaderResourceAccessor(), database);
    }

    private void clearCache() {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
    }
}
