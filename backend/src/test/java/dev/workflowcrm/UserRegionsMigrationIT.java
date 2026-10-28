package dev.workflowcrm;

import static dev.workflowcrm.ObjectPolicyMigrationSnapshot.*;
import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class UserRegionsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void keepsExistingAssignmentsAndRestoresSchemaAndDataAfterRollback() throws Exception {
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var baseline =
                    new Liquibase("db/changelog/object-list-test.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(baseline);
            }
        }
        try (var connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var fixture = new ObjectContactMigrationFixture(connection);
            fixture.seed();
            connection.commit();
            var originalSchema = schema(connection);
            var users = query(connection, "select to_jsonb(u)::text from users u order by id");
            var object = fixture.objectSnapshot(fixture.object);
            try (var migration = new Liquibase(
                    "db/changelog/users/20261013_user_regions.yaml", new ClassLoaderResourceAccessor(), db)) {
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from user_regions", Integer.class))
                        .isZero();
                fixture.jdbc.update(
                        "insert into user_regions(user_id,region_id) values(?,?)", fixture.manager, fixture.region);
                ObjectRegionBoundaryFixture.load(fixture.jdbc, fixture.region);
                connection.commit();
                var memberships = query(connection, "select to_jsonb(u)::text from user_regions u order by id");
                var polygons = query(connection, "select to_jsonb(b)::text from region_boundaries b order by id");
                update(migration);
                assertThat(query(connection, "select to_jsonb(u)::text from user_regions u order by id"))
                        .isEqualTo(memberships);
                assertThat(query(connection, "select to_jsonb(b)::text from region_boundaries b order by id"))
                        .isEqualTo(polygons);
                assertThat(query(connection, "select to_jsonb(u)::text from users u order by id"))
                        .isEqualTo(users);
                migration.rollback(1, new Contexts(), new LabelExpression());
                assertThat(schema(connection)).isEqualTo(originalSchema);
                assertThat(query(connection, "select to_jsonb(u)::text from users u order by id"))
                        .isEqualTo(users);
                assertThat(fixture.objectSnapshot(fixture.object)).isEqualTo(object);
                update(migration);
                assertThat(fixture.jdbc.queryForObject("select count(*) from user_regions", Integer.class))
                        .isZero();
                assertThat(fixture.jdbc.queryForObject("select count(*) from region_boundaries", Integer.class))
                        .isZero();
            }
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
