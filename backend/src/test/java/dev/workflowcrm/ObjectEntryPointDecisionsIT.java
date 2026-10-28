package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectEntryPointDecisionsIT extends ObjectEditTestSupport {
    @Test
    void onlyRegionalApproverCanApproveAndSelfApprovalIsForbidden() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        for (UUID actor : List.of(manager, colleague, contract, director, admin))
            decide(actor, object, request, "approve").andExpect(status().isForbidden());
        decide(lead, object, request, "approve").andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("APPROVED");
        decide(lead, object, request, "approve").andExpect(status().isConflict());
        assertThat(version(object)).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(4);
    }

    @Test
    void assignedManagerApprovalCannotBePerformedByColleague() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(lead, object);
        decide(colleague, object, request, "approve").andExpect(status().isForbidden());
        decide(manager, object, request, "approve").andExpect(status().isOk());
    }

    @Test
    void rejectionAndInitiatorCancellationLeavePointUnchanged() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        decide(lead, object, request, "cancel").andExpect(status().isForbidden());
        decide(lead, object, request, "reject").andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("REJECTED");
        request = request(manager, object);
        decide(manager, object, request, "cancel").andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("CANCELLED");
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isEqualTo(4);
    }

    @Test
    void editingObjectMakesRequestStaleAndDecisionReturnsCurrentVersion() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("STALE");
        var error = response(decide(lead, object, request, "approve").andExpect(status().isConflict()));
        assertThat(error.path("currentVersion").asLong()).isEqualTo(1);
        assertThat(card(manager, object).path("entrance").path("longitude").decimalValue())
                .isEqualByComparingTo("30.5");
    }

    @Test
    void changedAssignmentAndCrossObjectRequestCannotBeApproved() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(lead, object);
        UUID other = add("Інший об’єкт", manager, region);
        decide(manager, other, request, "approve").andExpect(status().isForbidden());
        jdbc.update("update objects set manager_id=? where id=?", colleague, object);
        decide(manager, object, request, "approve").andExpect(status().isForbidden());
        decide(colleague, object, request, "approve").andExpect(status().isForbidden());
        assertThat(requestStatus(request)).isEqualTo("PENDING");
    }

    @Test
    void decisionAuditFailureRollsBackCoordinatesAndRequest() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        jdbc.execute(
                "alter table object_status_audit add constraint test_point_audit check(event_type<>'ENTRY_POINT_REQUEST_APPROVED')");
        try {
            decide(lead, object, request, "approve").andExpect(status().isInternalServerError());
            assertThat(requestStatus(request)).isEqualTo("PENDING");
            assertThat(version(object)).isZero();
            assertThat(count("object_history")).isEqualTo(1);
        } finally {
            jdbc.execute("alter table object_status_audit drop constraint test_point_audit");
        }
    }
}
