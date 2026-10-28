package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.workflowcrm.objects.domain.TransitionRequiredField;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectSchemaChecks1IT extends ObjectSchemaTestSupport {
    @Test
    void seedsExactRefusalReasonsObjectTypesAndFileCategories() {
        assertThat(jdbc.queryForList("select name from refusal_reasons", String.class))
                .containsExactlyInAnyOrder(
                        "Комерційні умови",
                        "Умови договору",
                        "Тех. параметри",
                        "Малий потенціал",
                        "Програли конкурентам",
                        "Не погоджено на ІК");
        assertThat(jdbc.queryForList("select name from object_types where is_active", String.class))
                .containsExactlyInAnyOrder("Земельна ділянка", "Вбудоване приміщення", "Ангар", "ТЦ");
        assertThat(categories.findByActiveTrueOrderByName()).hasSize(5);
        assertThat(categories.findByCode("CONTRACTS").orElseThrow().isRestricted())
                .isTrue();
        assertThat(jdbc.queryForList("select name from file_categories where not is_restricted", String.class))
                .containsExactlyInAnyOrder("Медіа", "Технічна документація", "Правовстановлюючі", "Інше");
    }

    @Test
    void allowsManagerOnlyEarlyTransitionsAndRequiresRefusalReason() {
        var roleId = jdbc.queryForObject("select id from roles where code = 'MANAGER'", UUID.class);
        var permitted = jdbc.queryForList(
                "select transition.code from object_status_transitions transition "
                        + "join object_transition_roles permission on permission.transition_id = transition.id "
                        + "where permission.role_id = ? and transition.is_active",
                String.class,
                roleId);
        assertThat(permitted).hasSize(15);
        assertThat(permitted).noneMatch(code -> code.startsWith("MANAGER_IC_") || code.equals("SIGN_CONTRACT"));
        assertThat(transitions
                        .findByCode("MANAGER_FOUND_TO_REFUSED")
                        .orElseThrow()
                        .getRequiredFields())
                .containsExactly(TransitionRequiredField.REFUSAL_REASON);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_transitions t "
                                + "join object_statuses source on source.id = t.from_status_id "
                                + "join object_transition_roles p on p.transition_id = t.id "
                                + "where p.role_id = ? and source.code in ('IC_PREPARATION', 'COMMERCIAL_TERMS_APPROVAL', "
                                + "'CONTRACT_APPROVAL', 'CONTRACT_SIGNED', 'OPENED')",
                        Integer.class,
                        roleId))
                .isZero();
    }

    @Test
    void modelsLeadAndContractConfirmationAndSystemOnlyCommitteeTransitions() {
        var preparation = transitions.findByCode("REQUEST_IN_PROGRESS_TO_IC").orElseThrow();
        assertThat(preparation.isConfirmationRequired()).isTrue();
        assertThat(preparation.getConfirmingRoleId()).isEqualTo(roleId("REGIONAL_MANAGER"));
        assertThat(preparation.getRoleIds()).containsExactly(roleId("MANAGER"));
        var contract = transitions.findByCode("REQUEST_CONTRACT_APPROVAL").orElseThrow();
        assertThat(contract.getConfirmingRoleId()).isEqualTo(roleId("CONTRACT_MANAGER"));
        assertThat(contract.getRoleIds())
                .containsExactlyInAnyOrder(roleId("REGIONAL_MANAGER"), roleId("DEVELOPMENT_DIRECTOR"));
        var conditional = transitions.findByCode("IC_APPROVED_WITH_CONDITIONS").orElseThrow();
        assertThat(conditional.isSystemOnly()).isTrue();
        assertThat(conditional.getRoleIds()).isEmpty();
        assertThat(conditional.getRequiredFields())
                .containsExactlyInAnyOrder(
                        TransitionRequiredField.INVESTMENT_COMMITTEE_DATE,
                        TransitionRequiredField.INVESTMENT_COMMITTEE_CONDITIONS);
        assertThat(transitions.findByCode("IC_REFUSED").orElseThrow().getRequiredFields())
                .containsExactlyInAnyOrder(
                        TransitionRequiredField.INVESTMENT_COMMITTEE_DATE, TransitionRequiredField.REFUSAL_REASON);
        assertThat(transitions
                        .findByCode("IC_REFUSED")
                        .orElseThrow()
                        .getFixedRefusalReason()
                        .getCode())
                .isEqualTo("IC_NOT_APPROVED");
    }
}
