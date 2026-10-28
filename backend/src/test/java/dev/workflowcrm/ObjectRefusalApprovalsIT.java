package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectRefusalApprovalsIT extends ObjectStatusTasksSupport {
    UUID request() throws Exception {
        return UUID.fromString(response(change(manager, command("IC_PREPARATION")))
                .path("approvalRequestId")
                .asText());
    }

    String requestState(UUID id) {
        return jdbc.queryForObject("select status from status_approval_requests where id=?", String.class, id);
    }

    Map<String, Object> refusal() {
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        return body;
    }

    @Test
    void refusalCancelsPendingRequestAndNotifiesInitiatorExactlyOnce() throws Exception {
        UUID id = request();
        task("IN_PROGRESS");
        var body = refusal();
        change(lead, body).andExpect(status().isOk());
        assertThat(requestState(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select decided_by_id from status_approval_requests where id=?", UUID.class, id))
                .isEqualTo(lead);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_audit where event_type='STATUS_REQUEST_CANCELLED'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(events("OBJECT_STATUS_APPROVAL_RESOLVED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=? and event_type='OBJECT_STATUS_APPROVAL_CANCELLED'",
                        Integer.class,
                        manager))
                .isEqualTo(1);
        approval(lead, id, "approve", Map.of()).andExpect(status().isConflict());
        change(lead, body).andExpect(status().isConflict());
        assertThat(requestState(id)).isEqualTo("CANCELLED");
        assertThat(events("OBJECT_STATUS_APPROVAL_RESOLVED")).isEqualTo(1);
        assertThat(state()).isEqualTo("REFUSED");
    }

    @Test
    void invalidRefusalLeavesRequestTasksAndNotificationsUntouched() throws Exception {
        UUID id = request();
        task("WAITING_APPROVAL");
        var tasks = taskSnapshot();
        int notifications = count("user_notifications");
        change(manager, command("REFUSED")).andExpect(status().isUnprocessableEntity());
        assertThat(requestState(id)).isEqualTo("PENDING");
        assertThat(taskSnapshot()).isEqualTo(tasks);
        assertThat(count("user_notifications")).isEqualTo(notifications);
        assertThat(state()).isEqualTo("FOUND");
        assertThat(version()).isZero();
    }

    @Test
    void cancellationEventFailureRollsBackWholeRefusal() throws Exception {
        UUID id = request();
        task("NEW");
        var tasks = taskSnapshot();
        int notifications = count("user_notifications"),
                audit = count("object_status_audit"),
                events = count("event_outbox");
        jdbc.execute(
                "alter table object_status_audit add constraint test_refusal_failure check(event_type<>'STATUS_REQUEST_CANCELLED')");
        try {
            change(manager, refusal()).andExpect(status().isInternalServerError());
            assertThat(requestState(id)).isEqualTo("PENDING");
            assertThat(state()).isEqualTo("FOUND");
            assertThat(version()).isZero();
            assertThat(taskSnapshot()).isEqualTo(tasks);
            assertThat(count("object_history")).isEqualTo(1);
            assertThat(count("object_refusal_tags")).isZero();
            assertThat(count("user_notifications")).isEqualTo(notifications);
            assertThat(count("object_status_audit")).isEqualTo(audit);
            assertThat(count("event_outbox")).isEqualTo(events);
        } finally {
            jdbc.execute("alter table object_status_audit drop constraint test_refusal_failure");
        }
    }

    @Test
    void lateRefusalCancelsContractApprovalButForbiddenManagerDoesNot() throws Exception {
        state("COMMERCIAL_TERMS_APPROVAL");
        UUID id = UUID.fromString(response(change(lead, command("CONTRACT_APPROVAL")))
                .path("approvalRequestId")
                .asText());
        change(manager, refusal()).andExpect(status().isUnprocessableEntity());
        assertThat(requestState(id)).isEqualTo("PENDING");
        change(director, refusal()).andExpect(status().isOk());
        assertThat(requestState(id)).isEqualTo("CANCELLED");
        approval(contract, id, "approve", Map.of()).andExpect(status().isConflict());
    }
}
