package dev.workflowcrm;

import dev.workflowcrm.files.infrastructure.FileCategoryRepository;
import dev.workflowcrm.objects.domain.EntranceCoordinates;
import dev.workflowcrm.objects.domain.ObjectContact;
import dev.workflowcrm.objects.domain.ObjectRegistration;
import dev.workflowcrm.objects.domain.PropertyAreas;
import dev.workflowcrm.objects.domain.RealEstateObject;
import dev.workflowcrm.objects.infrastructure.ObjectHistoryRepository;
import dev.workflowcrm.objects.infrastructure.ObjectStatusRepository;
import dev.workflowcrm.objects.infrastructure.ObjectStatusTransitionRepository;
import dev.workflowcrm.objects.infrastructure.ObjectTypeRepository;
import dev.workflowcrm.objects.infrastructure.RealEstateObjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class ObjectSchemaTestSupport {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    @Autowired
    protected RealEstateObjectRepository objects;

    @Autowired
    protected ObjectStatusRepository statuses;

    @Autowired
    protected ObjectTypeRepository types;

    @Autowired
    ObjectStatusTransitionRepository transitions;

    @Autowired
    protected ObjectHistoryRepository history;

    @Autowired
    protected FileCategoryRepository categories;

    protected RealEstateObject newObject() {
        UUID regionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into regions(id, code, name) values (?, ?, 'Test region')", regionId, regionId.toString());
        jdbc.update(
                "insert into users(id, email, password_hash, full_name, role_id, status) "
                        + "values (?, ?, 'test-hash', 'Test Manager', ?, 'ACTIVE')",
                userId,
                userId + "@example.com",
                roleId("MANAGER"));
        UserRegionTestData.assign(jdbc, userId, regionId);
        return new RealEstateObject(
                new ObjectRegistration(
                        "Test property",
                        types.findByCode("LAND_PLOT").orElseThrow(),
                        "Test address",
                        regionId,
                        userId,
                        userId,
                        new EntranceCoordinates(new BigDecimal("30.5234"), new BigDecimal("50.4501")),
                        new PropertyAreas(new BigDecimal("650.00"), null),
                        new ObjectContact("Test owner", "Owner", "+380000000000", null)),
                statuses.findByCode("FOUND").orElseThrow());
    }

    protected UUID roleId(String code) {
        return jdbc.queryForObject("select id from roles where code = ?", UUID.class, code);
    }
}
