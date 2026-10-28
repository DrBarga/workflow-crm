package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.FastCheckService;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "app.objects.deletion.worker-enabled=false",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=6379",
            "spring.data.redis.password=test",
            "spring.rabbitmq.host=localhost",
            "spring.rabbitmq.port=5672",
            "spring.rabbitmq.username=test",
            "spring.rabbitmq.password=test",
            "app.minio.endpoint=http://localhost:9000",
            "app.minio.access-key=test-access-key",
            "app.minio.secret-key=test-secret-key",
            "app.security.jwt.secret=test-jwt-signing-secret-with-32-bytes"
        })
class LiquibaseMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("workflow_crm")
            .withUsername("workflow_app")
            .withPassword("workflow_test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpringLiquibase liquibase;

    @Autowired
    private DataSource dataSource;

    @Test
    void rollsBackAuthSchemaAndReappliesIt() throws Exception {
        try (var connection = dataSource.getConnection();
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            migration.rollback(appliedChangesetCount() - 1, new Contexts(), new LabelExpression());
            assertThat(tableExists("telegram_binding_events")).isFalse();
            assertThat(tableExists("password_reset_tokens")).isFalse();
            assertThat(tableExists("user_invitations")).isFalse();
            assertThat(tableExists("user_access_history")).isFalse();
            assertThat(tableExists("users")).isFalse();
            assertThat(tableExists("roles")).isFalse();
            assertThat(tableExists("regions")).isFalse();
            assertThat(tableExists("app_settings")).isTrue();
        } finally {
            // Recreate the startup check after an in-process rollback.
            Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
            liquibase.afterPropertiesSet();
        }
        assertThat(tableExists("users")).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from roles", Integer.class))
                .isEqualTo(6);
    }

    @Test
    void rollsBackRecentAuthMigrationsAndPreservesUsers() throws Exception {
        try (var connection = dataSource.getConnection();
                var migration = new Liquibase(
                        "db/changelog/db.changelog-master.yaml",
                        new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection))) {
            migration.rollback(
                    changesetsSince("20260916_auth_password_recovery"), new Contexts(), new LabelExpression());
            assertThat(tableExists("telegram_binding_events")).isFalse();
            assertThat(tableExists("password_reset_tokens")).isFalse();
            assertThat(tableExists("user_invitations")).isTrue();
            assertThat(tableExists("user_access_history")).isTrue();
            assertThat(tableExists("users")).isTrue();
        } finally {
            Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
            liquibase.afterPropertiesSet();
        }
        assertThat(tableExists("password_reset_tokens")).isTrue();
        assertThat(tableExists("telegram_binding_events")).isTrue();
    }

    @Test
    void refusesChecksumMismatch() {
        String id = "20260904_users_auth_model";
        String checksum =
                jdbcTemplate.queryForObject("select md5sum from databasechangelog where id = ?", String.class, id);
        try {
            jdbcTemplate.update(
                    "update databasechangelog set md5sum = ? where id = ?", "9:00000000000000000000000000000000", id);
            assertThatThrownBy(liquibase::afterPropertiesSet).isInstanceOf(LiquibaseException.class);
        } finally {
            jdbcTemplate.update("update databasechangelog set md5sum = ? where id = ?", checksum, id);
        }
    }

    @Test
    void appliesBaselineOnceAndKeepsSchemaStableOnSecondRun() throws LiquibaseException {
        int appliedChangesets = appliedChangesetCount();

        assertThat(appliedChangesets).isPositive();
        assertThat(tableExists("app_settings")).isTrue();
        assertThat(columnType("app_settings", "created_at")).isEqualTo("timestamp with time zone");

        liquibase.afterPropertiesSet();

        assertThat(appliedChangesetCount()).isEqualTo(appliedChangesets);
        assertThat(tableExists("app_settings")).isTrue();
    }

    private int appliedChangesetCount() {
        return jdbcTemplate.queryForObject("select count(*) from databasechangelog", Integer.class);
    }

    private int changesetsSince(String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog where orderexecuted >= "
                        + "(select orderexecuted from databasechangelog where id = ?)",
                Integer.class,
                id);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables " + "where table_schema = 'public' and table_name = ?",
                Integer.class,
                tableName);
        return count != null && count == 1;
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class,
                tableName,
                columnName);
    }
}
