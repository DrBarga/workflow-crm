package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectCardTransitionsIT extends ObjectCardTestSupport {
    @Test
    void managerSeesConfirmationAndRequiredRefusalFieldFromTransitionTable() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IN_PROGRESS");
        var c = card(manager, object);
        var rules = c.path("availableTransitions");
        var confirmation = new ArrayList<String>();
        rules.forEach(r -> {
            if (r.path("status").asText().equals("IC_PREPARATION")) {
                assertThat(r.path("confirmationRequired").asBoolean()).isTrue();
                assertThat(r.path("confirmingRoleId").asText()).isNotBlank();
                confirmation.add(r.path("code").asText());
            }
            if (r.path("status").asText().equals("REFUSED"))
                assertThat(r.path("requiredFields").toString()).contains("refusalReasonId");
        });
        assertThat(confirmation).hasSize(1);
    }

    @Test
    void systemOnlyInactiveAndOtherRoleTransitionsAreAbsent() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        var codes = transitionCodes(card(manager, object));
        assertThat(codes).doesNotContain("IC_APPROVED", "IC_APPROVED_WITH_CONDITIONS", "IC_REFUSED");
        state(object, "FOUND");
        jdbc.update("update object_status_transitions set is_active=false where code='MANAGER_FOUND_TO_IN_PROGRESS'");
        try {
            assertThat(transitionCodes(card(manager, object))).doesNotContain("MANAGER_FOUND_TO_IN_PROGRESS");
        } finally {
            jdbc.update(
                    "update object_status_transitions set is_active=true where code='MANAGER_FOUND_TO_IN_PROGRESS'");
        }
        assertThat(card(ceo, object).path("availableTransitions")).isEmpty();
    }

    @Test
    void historicalTasksHideUnavailableRollbackButNotRefusal() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IN_PROGRESS");
        assertThat(transitionCodes(card(manager, object))).contains("MANAGER_IN_PROGRESS_TO_FOUND");
        tasks(object);
        assertThat(transitionCodes(card(manager, object))).doesNotContain("MANAGER_IN_PROGRESS_TO_FOUND");
        assertThat(card(manager, object).path("availableTransitions").toString())
                .contains("REFUSED");
    }

    @Test
    void pauseResumeAndEarlyRefusalUseSameConditionsAsStatusWrite() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "PAUSED");
        jdbc.update(
                "update objects set status_before_pause_id=(select id from object_statuses where code='IN_PROGRESS') where id=?",
                object);
        var c = card(manager, object);
        assertThat(c.path("availableTransitions").toString()).contains("IN_PROGRESS");
        state(object, "REFUSED");
        jdbc.update(
                "update objects set status_before_refusal_id=(select id from object_statuses where code='IC_PREPARATION') where id=?",
                object);
        assertThat(card(manager, object).path("availableTransitions")).isEmpty();
        assertThat(card(lead, object).path("availableTransitions")).isNotEmpty();
    }

    @Test
    void contractRoleReceivesSigningRequirementsAndNeverManualRefusal() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "CONTRACT_APPROVAL");
        var c = card(contract, object);
        assertThat(c.path("availableTransitions").toString()).contains("contractSignedDate", "contractTypeId");
        assertThat(c.path("availableTransitions").toString()).doesNotContain("REFUSED");
    }
}
