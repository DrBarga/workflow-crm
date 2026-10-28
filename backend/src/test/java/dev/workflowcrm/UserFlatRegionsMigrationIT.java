package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class UserFlatRegionsMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradeRollbackAndReapplicationPreserveAssignmentsAccountsAndDirectory() throws Exception {
        baseline();
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var migration = new Liquibase(
                        "db/changelog/users/20261015_flat_user_regions.yaml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            update(migration);
            assertThat(jdbc.queryForObject("select count(*) from regions", Integer.class))
                    .isZero();
            migration.rollback(1, new Contexts(), new LabelExpression());
            UUID first = UUID.randomUUID(),
                    second = UUID.randomUUID(),
                    third = UUID.randomUUID(),
                    user = UUID.randomUUID();
            jdbc.update(
                    "insert into regions(id,code,name) values (?,'FIRST','First'),(?,'SECOND','Second'),(?,'THIRD','Third')",
                    first,
                    second,
                    third);
            jdbc.update("update regions set parent_id=? where id=?", first, second);
            jdbc.update(
                    "insert into users(id,email,password_hash,full_name,role_id,region_id,status) select ?,'migration@example.com','unchanged-hash','Existing User',id,?,'DISABLED' from roles where code='REGIONAL_MANAGER'",
                    user,
                    first);
            jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", user, second);
            String account = jdbc.queryForObject(
                    "select (to_jsonb(u)-'region_id')::text from users u where id=?", String.class, user);
            var directory = jdbc.queryForList(
                    "select (to_jsonb(r)-'parent_id')::text from regions r order by id", String.class);
            var roles = jdbc.queryForList("select id::text||':'||code from roles order by id", String.class);
            connection.commit();
            update(migration);
            update(migration);
            assertThat(jdbc.queryForList("select region_id from user_regions where user_id=?", UUID.class, user))
                    .containsExactlyInAnyOrder(first, second);
            assertThat(jdbc.queryForObject("select to_jsonb(u)::text from users u where id=?", String.class, user))
                    .isEqualTo(account);
            assertThat(jdbc.queryForList("select to_jsonb(r)::text from regions r order by id", String.class))
                    .isEqualTo(directory);
            assertThat(jdbc.queryForList("select id::text||':'||code from roles order by id", String.class))
                    .isEqualTo(roles);
            assertThat(jdbc.queryForObject(
                            "select count(*) from information_schema.columns where (table_name='users' and column_name='region_id') or (table_name='regions' and column_name='parent_id')",
                            Integer.class))
                    .isZero();
            jdbc.update("delete from user_regions where user_id=? and region_id=?", user, first);
            jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", user, third);
            connection.commit();
            migration.rollback(1, new Contexts(), new LabelExpression());
            assertThat(jdbc.queryForObject("select region_id from users where id=?", UUID.class, user))
                    .isIn(second, third);
            assertThat(jdbc.queryForObject("select parent_id from regions where id=?", UUID.class, second))
                    .isEqualTo(first);
            assertThat(jdbc.queryForObject("select count(*) from user_regions where user_id=?", Integer.class, user))
                    .isEqualTo(2);
            update(migration);
            assertThat(jdbc.queryForList("select region_id from user_regions where user_id=?", UUID.class, user))
                    .containsExactlyInAnyOrder(second, third);
            assertThat(jdbc.queryForList("select name from roles", String.class))
                    .containsExactlyInAnyOrder(
                            "Менеджер",
                            "Провідний менеджер регіону",
                            "Директор департаменту розвитку",
                            "CEO",
                            "Погодження договорів",
                            "Адміністратор");
        }
    }

    private void baseline() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            update(migration);
        }
    }

    private void update(Liquibase migration) throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
