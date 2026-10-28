package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
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
class UserSchemaMigrationIT {

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

    @Test
    void createsUserRoleAndRegionSchema() {
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("roles")).isTrue();
        assertThat(tableExists("regions")).isTrue();
        assertThat(tableExists("user_invitations")).isTrue();
        assertThat(tableExists("password_reset_tokens")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns where table_name='users' and column_name='region_id'",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.columns where table_name='regions' and column_name='parent_id'",
                        Integer.class))
                .isZero();
        assertThat(columnIsNullable("users", "password_setup_required")).isFalse();
        assertThat(columnIsNullable("users", "telegram_chat_id")).isTrue();
        assertThat(foreignKeyExists("users", "role_id", "roles")).isTrue();
        assertThat(foreignKeyExists("user_regions", "region_id", "regions")).isTrue();
        assertThat(foreignKeyExists("user_regions", "user_id", "users")).isTrue();
    }

    @Test
    void seedsAllApplicationRoles() {
        List<String> roleCodes = jdbcTemplate.queryForList("select code from roles order by code", String.class);

        assertThat(roleCodes)
                .containsExactlyInAnyOrder(
                        "MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN");
    }

    @Test
    void enforcesCaseInsensitiveEmailUniqueness() {
        String roleId = jdbcTemplate.queryForObject("select id::text from roles where code = 'MANAGER'", String.class);

        insertUser("00000000-0000-0000-0001-000000000001", "manager@example.com", roleId);

        assertThatThrownBy(() -> insertUser("00000000-0000-0000-0001-000000000002", "MANAGER@example.com", roleId))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertUser(String id, String email, String roleId) {
        jdbcTemplate.update(
                "insert into users (id, email, password_hash, full_name, role_id, status) "
                        + "values (?::uuid, ?, ?, ?, ?::uuid, 'ACTIVE')",
                id,
                email,
                "$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234",
                "Test Manager",
                roleId);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables " + "where table_schema = 'public' and table_name = ?",
                Integer.class,
                tableName);
        return count != null && count == 1;
    }

    private boolean columnIsNullable(String tableName, String columnName) {
        String nullable = jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class,
                tableName,
                columnName);
        return "YES".equals(nullable);
    }

    private boolean foreignKeyExists(String tableName, String columnName, String referencedTable) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) "
                        + "from information_schema.table_constraints tc "
                        + "join information_schema.key_column_usage kcu "
                        + "on tc.constraint_name = kcu.constraint_name and tc.constraint_schema = kcu.constraint_schema "
                        + "join information_schema.constraint_column_usage ccu "
                        + "on tc.constraint_name = ccu.constraint_name and tc.constraint_schema = ccu.constraint_schema "
                        + "where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public' "
                        + "and tc.table_name = ? and kcu.column_name = ? and ccu.table_name = ?",
                Integer.class,
                tableName,
                columnName,
                referencedTable);
        return count != null && count == 1;
    }
}
