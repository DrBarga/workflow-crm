package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.workflowcrm.objects.domain.UnfinishedTaskAction;
import dev.workflowcrm.objects.infrastructure.ObjectStatusTransitionRepository;
import dev.workflowcrm.objects.infrastructure.RefusalReasonRepository;
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
class ObjectTransitionPolicyIT {
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
    ObjectStatusTransitionRepository transitions;

    @Autowired
    RefusalReasonRepository reasons;

    @Test
    void removesSeededTriggersAndPreservesCommitteeSystemReason() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_name='object_statuses' and column_name='is_task_generation_trigger'",
                        Integer.class))
                .isZero();
        assertThat(reasons.findByCode("IC_NOT_APPROVED").orElseThrow().isSystemOnly())
                .isTrue();
        assertThat(jdbc.queryForList("select code from refusal_reasons where is_system_only", String.class))
                .containsExactly("IC_NOT_APPROVED");
    }

    @Test
    void matchesAllSeventeenRulesIncludingRolesConditionsAndTaskActions() throws Exception {
        var expected = new ClassPathPolicyMatrix().read();
        var actual = jdbc.queryForList(
                """
                select t.code || '|' || source.code || '|' || target.code || '|' || t.unfinished_task_action
                    || '|' || t.is_unavailable_after_task_creation || '|' || coalesce(t.condition_code, '-')
                    || '|' || coalesce((select string_agg(r.code, ',' order by r.code) from object_transition_roles p
                        join roles r on r.id=p.role_id where p.transition_id=t.id), '-')
                    || '|' || coalesce(confirmer.code, '-') || '|' || t.is_system_only || '|' || t.is_rollback
                    || '|' || coalesce(t.investment_committee_decision, '-')
                    || '|' || coalesce((select string_agg(f.field_code, ',' order by f.field_code)
                        from object_transition_required_fields f where f.transition_id=t.id), '-')
                    || '|' || coalesce(reason.code, '-')
                from object_status_transitions t join object_statuses source on source.id=t.from_status_id
                join object_statuses target on target.id=t.to_status_id
                left join roles confirmer on confirmer.id=t.confirming_role_id
                left join refusal_reasons reason on reason.id=t.fixed_refusal_reason_id
                where t.is_active
                """,
                String.class);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(transitions
                        .findByCode("MANAGER_IN_PROGRESS_TO_FOUND")
                        .orElseThrow()
                        .isUnavailableAfterTaskCreation())
                .isTrue();
        assertThat(transitions
                        .findByCode("LEAD_IN_PROGRESS_TO_FOUND")
                        .orElseThrow()
                        .isUnavailableAfterTaskCreation())
                .isFalse();
        assertThat(transitions.findByCode("SIGN_CONTRACT").orElseThrow().getUnfinishedTaskAction())
                .isEqualTo(UnfinishedTaskAction.NONE);
    }

    @Test
    void rejectsUnknownTaskActionAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbc.update("update object_status_transitions set unfinished_task_action='IGNORE' "
                        + "where code='SIGN_CONTRACT'"))
                .isInstanceOf(DataAccessException.class);
    }
}
