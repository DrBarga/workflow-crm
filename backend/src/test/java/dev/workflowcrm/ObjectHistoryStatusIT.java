package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.objects.domain.ObjectHistoryService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectHistoryStatusIT extends ObjectStatusTestSupport {
    @Autowired
    ObjectHistoryService history;

    @Test
    void statusApprovalRequestAndRejectionAppearWithoutChangingStatus() throws Exception {
        state("IN_PROGRESS");
        UUID request = UUID.fromString(
                response(change(manager, command("IC_PREPARATION")).andExpect(status().isOk()))
                        .path("approvalRequestId")
                        .asText());
        approval(lead, request, "reject", Map.of()).andExpect(status().isOk());
        assertThat(history.get(manager, object, null, 50).items())
                .extracting(e -> e.field())
                .containsExactly("STATUS_APPROVAL_REQUESTED", "STATUS_APPROVAL_REJECTED");
        assertThat(state()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void backwardTransitionExposesClearedCommitteeValues() throws Exception {
        state("COMMERCIAL_TERMS_APPROVAL");
        jdbc.update(
                "update objects set investment_committee_decision='APPROVED',investment_committee_date='2026-10-01' where id=?",
                object);
        change(lead, command("IC_PREPARATION")).andExpect(status().isOk());
        var rows = history.get(manager, object, null, 50).items();
        assertThat(rows).anySatisfy(e -> {
            assertThat(e.field()).isEqualTo("IC_DATE");
            assertThat(e.previousValue()).isEqualTo("2026-10-01");
            assertThat(e.newValue()).isNull();
        });
        assertThat(rows).anySatisfy(e -> assertThat(e.field()).isEqualTo("STATUS"));
    }
}
