package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.objects.domain.TransitionCondition;
import dev.workflowcrm.objects.domain.TransitionRequiredField;
import org.junit.jupiter.api.Test;

class ObjectSchemaChecks2IT extends ObjectSchemaTestSupport {
    @Test
    void limitsSigningAndReturningAndUsesDocumentedOpeningDefaults() {
        var signing = transitions.findByCode("SIGN_CONTRACT").orElseThrow();
        assertThat(signing.getRoleIds())
                .containsExactlyInAnyOrder(
                        roleId("REGIONAL_MANAGER"), roleId("CONTRACT_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        assertThat(signing.getRequiredFields())
                .containsExactlyInAnyOrder(
                        TransitionRequiredField.CONTRACT_SIGNED_DATE, TransitionRequiredField.CONTRACT_TYPE,
                        TransitionRequiredField.COMMISSIONING_DATE,
                                TransitionRequiredField.CONSTRUCTION_ACCEPTANCE_PLANNED_DATE,
                        TransitionRequiredField.CONSTRUCTION_TERM_DAYS, TransitionRequiredField.OPENING_PLANNED_DATE);
        var returning = transitions.findByCode("RETURN_CONTRACT_APPROVAL").orElseThrow();
        assertThat(returning.isRollback()).isTrue();
        assertThat(returning.getRoleIds())
                .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        var opening = transitions.findByCode("OPEN_OBJECT").orElseThrow();
        assertThat(opening.isActive()).isTrue();
        assertThat(opening.getRoleIds())
                .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        assertThat(opening.getRequiredFields()).containsExactly(TransitionRequiredField.OPENING_DATE);
    }

    @Test
    void givesDirectorTheSameActiveWorkflowRulesAsLeadAndKeepsCeoReadOnly() {
        var leadRules = jdbc.queryForList(
                "select t.code from object_status_transitions t "
                        + "join object_transition_roles p on p.transition_id = t.id where p.role_id = ? and t.is_active",
                String.class,
                roleId("REGIONAL_MANAGER"));
        var directorRules = jdbc.queryForList(
                "select t.code from object_status_transitions t "
                        + "join object_transition_roles p on p.transition_id = t.id where p.role_id = ? and t.is_active",
                String.class,
                roleId("DEVELOPMENT_DIRECTOR"));
        assertThat(directorRules).containsExactlyInAnyOrderElementsOf(leadRules);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_transition_roles where role_id = ?", Integer.class, roleId("CEO")))
                .isZero();
    }

    @Test
    void modelsPauseRestorationWithoutLettingManagerBypassAdvancedStatusPermissions() {
        assertThat(transitions
                        .findByCode("MANAGER_PAUSED_TO_FOUND")
                        .orElseThrow()
                        .getCondition())
                .isNull();
        assertThat(transitions
                        .findByCode("MANAGER_PAUSED_TO_IN_PROGRESS")
                        .orElseThrow()
                        .getCondition())
                .isEqualTo(TransitionCondition.RESUME_PREVIOUS_STATUS);
        assertThat(transitions.findByCode("LEAD_PAUSED_TO_IC").orElseThrow().getCondition())
                .isEqualTo(TransitionCondition.RESUME_PREVIOUS_STATUS);
        assertThat(transitions
                        .findByCode("MANAGER_PAUSED_TO_REFUSED")
                        .orElseThrow()
                        .getCondition())
                .isEqualTo(TransitionCondition.EARLY_STATUS_BEFORE_PAUSE);
        assertThat(transitions.findByCode("REQUEST_PAUSED_TO_IC").orElseThrow().isActive())
                .isFalse();
        assertThat(transitions.findByCode("RESUME_SIGNED").orElseThrow().getRoleIds())
                .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        assertThat(transitions.findByCode("RESUME_SIGNED").orElseThrow().getCondition())
                .isEqualTo(TransitionCondition.RESUME_PREVIOUS_STATUS);
        assertThat(jdbc.queryForList(
                        "select source.code from object_status_transitions t "
                                + "join object_statuses source on source.id = t.from_status_id "
                                + "join object_statuses target on target.id = t.to_status_id "
                                + "where target.code = 'PAUSED' and t.is_active",
                        String.class))
                .containsExactlyInAnyOrder(
                        "FOUND",
                        "IN_PROGRESS",
                        "REFUSED",
                        "IC_PREPARATION",
                        "COMMERCIAL_TERMS_APPROVAL",
                        "CONTRACT_APPROVAL",
                        "CONTRACT_SIGNED",
                        "OPENED",
                        "REFUSED");
    }
}
