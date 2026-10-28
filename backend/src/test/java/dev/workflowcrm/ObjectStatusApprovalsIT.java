package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusApprovalsIT extends ObjectStatusTasksSupport {
    UUID request() throws Exception {
        var result = response(change(manager, command("IC_PREPARATION")).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isFalse();
        return UUID.fromString(result.path("approvalRequestId").asText());
    }

    String requestState(UUID id) {
        return jdbc.queryForObject("select status from status_approval_requests where id=?", String.class, id);
    }

    @Test
    void requestIsIdempotentAndOnlyLeadInObjectRegionCanApprove() throws Exception {
        var id = request();
        assertThat(request()).isEqualTo(id);
        assertThat(state()).isEqualTo("FOUND");
        approval(manager, id, "approve", Map.of()).andExpect(status().isForbidden());
        approval(foreignLead, id, "approve", Map.of()).andExpect(status().isForbidden());
        approval(lead, id, "approve", Map.of()).andExpect(status().isOk());
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(requestState(id)).isEqualTo("APPROVED");
        approval(lead, id, "approve", Map.of()).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "cancel"})
    void rejectingOrCancellingDoesNotChangeObjectOrTasks(String decision) throws Exception {
        var id = request();
        approval("cancel".equals(decision) ? manager : lead, id, decision, Map.of())
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("FOUND");
        assertThat(version()).isZero();
        assertThat(taskSnapshot()).isEmpty();
        assertThat(requestState(id)).isEqualTo("cancel".equals(decision) ? "CANCELLED" : "REJECTED");
    }

    @Test
    void approverMustConfirmFreshTaskPreview() throws Exception {
        template("IC_PREPARATION", null);
        var command = command("IC_PREPARATION");
        var id = UUID.fromString(
                response(change(manager, command)).path("approvalRequestId").asText());
        var preview = response(approval(lead, id, "approve", Map.of()).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(requestState(id)).isEqualTo("PENDING");
        assertThat(taskSnapshot()).isEmpty();
        approval(
                        lead,
                        id,
                        "approve",
                        Map.of(
                                "confirmationToken",
                                preview.path("confirmationToken").asText()))
                .andExpect(status().isOk());
        assertThat(taskSnapshot()).hasSize(1);
        assertThat(requestState(id)).isEqualTo("APPROVED");
    }

    @Test
    void changesInvalidatePendingRequestAndDisabledInitiatorCannotBeApproved() throws Exception {
        var id = request();
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        approval(lead, id, "approve", Map.of()).andExpect(status().isForbidden());
        jdbc.update("update users set status='ACTIVE' where id=?", manager);
        change(lead, command("IN_PROGRESS")).andExpect(status().isOk());
        assertThat(requestState(id)).isEqualTo("STALE");
        approval(lead, id, "approve", Map.of()).andExpect(status().isConflict());
    }

    @Test
    void contractApprovalRequiresConfiguredContractRoleConfirmation() throws Exception {
        state("COMMERCIAL_TERMS_APPROVAL");
        var result = response(change(lead, command("CONTRACT_APPROVAL")).andExpect(status().isOk()));
        var id = UUID.fromString(result.path("approvalRequestId").asText());
        approval(lead, id, "approve", Map.of()).andExpect(status().isForbidden());
        approval(contract, id, "approve", Map.of()).andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
    }
}
