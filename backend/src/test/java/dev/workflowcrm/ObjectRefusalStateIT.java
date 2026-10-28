package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.workflowcrm.objects.domain.RealEstateObject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ObjectRefusalStateIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsPreviousRefusalAndPauseStatusesIndependentlyWithoutLosingVersion() {
        var object = existingObject();
        assertThat(entityManager.find(RealEstateObject.class, object).getStatusBeforeRefusal())
                .isNull();
        jdbc.update(
                "update objects set status_before_refusal_id=(select id from object_statuses where code='IC_PREPARATION'), "
                        + "status_before_pause_id=(select id from object_statuses where code='IN_PROGRESS') where id=?",
                object);
        entityManager.clear();
        var persisted = entityManager.find(RealEstateObject.class, object);
        assertThat(persisted.getStatusBeforeRefusal().getCode()).isEqualTo("IC_PREPARATION");
        assertThat(persisted.getStatusBeforePause().getCode()).isEqualTo("IN_PROGRESS");
        persisted.rename("Updated after refusal");
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(RealEstateObject.class, object).getVersion())
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status_before_refusal_id from objects where id=?", UUID.class, object))
                .isEqualTo(persisted.getStatusBeforeRefusal().getId());
    }

    @Test
    void previousRefusalStatusUsesRestrictiveForeignKeyAndIndex() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.referential_constraints "
                                + "where constraint_name='objects_status_before_refusal_id_fkey' and delete_rule='RESTRICT'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname='public' "
                                + "and indexname='ix_objects_status_before_refusal'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsDanglingPreviousRefusalStatus() {
        var object = existingObject();
        assertThatThrownBy(() -> jdbc.update(
                        "update objects set status_before_refusal_id=? where id=?", UUID.randomUUID(), object))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID existingObject() {
        var region = UUID.randomUUID();
        var manager = UUID.randomUUID();
        var object = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values (?,?,'Region')", region, region.toString());
        jdbc.update(
                "insert into users(id,email,password_hash,full_name,role_id,status) select ?,?,'test-hash','Manager',id,'ACTIVE' "
                        + "from roles where code='MANAGER'",
                manager,
                manager + "@example.com");
        jdbc.update(
                "insert into objects(id,region_id,manager_id,author_id,status_id,entrance_longitude,entrance_latitude,object_area) "
                        + "select ?,?,?,?,id,30.5,50.4,100 from object_statuses where code='REFUSED'",
                object,
                region,
                manager,
                manager);
        return object;
    }
}
