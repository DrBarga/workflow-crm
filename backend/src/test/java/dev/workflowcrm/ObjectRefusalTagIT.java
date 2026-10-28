package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectRefusalTagIT extends ObjectStatusTasksSupport {
    Map<String, Object> refusal(UUID reason) {
        var body = command("REFUSED");
        body.put("refusalReasonId", reason);
        return body;
    }

    @Test
    void pausePreservesTagAndNewRefusalRequiresFreshReason() throws Exception {
        UUID reason = reason();
        change(manager, refusal(reason)).andExpect(status().isOk());
        change(manager, command("PAUSED")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select reason_id from object_refusal_tags where object_id=?", UUID.class, object))
                .isEqualTo(reason);
        change(manager, command("REFUSED")).andExpect(status().isOk());
        assertThat(count("object_refusal_tags")).isEqualTo(1);
        change(manager, command("FOUND")).andExpect(status().isOk());
        change(manager, command("REFUSED")).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("object_refusal_tags")).isZero();
    }

    @Test
    void deactivationDoesNotHideExistingRefusalTagFromApiOrAnalytics() throws Exception {
        UUID reason = reason();
        change(manager, refusal(reason)).andExpect(status().isOk());
        jdbc.update("update refusal_reasons set is_active=false where id=?", reason);
        try {
            mvc.perform(get("/api/objects/" + object + "/status").cookie(cookie(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refusalTag.id").value(reason.toString()));
            assertThat(jdbc.queryForObject(
                            "select reason_id from object_refusal_tags where object_id=?", UUID.class, object))
                    .isEqualTo(reason);
        } finally {
            jdbc.update("update refusal_reasons set is_active=true where id=?", reason);
        }
    }

    @Test
    void previewDoesNotCancelTasksOrApprovalBeforeConfirmation() throws Exception {
        UUID request = UUID.fromString(response(change(manager, command("IC_PREPARATION")))
                .path("approvalRequestId")
                .asText());
        UUID task = task("NEW");
        template("REFUSED", null);
        var before = taskSnapshot();
        var body = refusal(reason());
        var preview = response(change(manager, body).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(state()).isEqualTo("FOUND");
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from status_approval_requests where id=?", String.class, request))
                .isEqualTo("PENDING");
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        change(manager, body).andExpect(status().isOk());
        assertThat(state()).isEqualTo("REFUSED");
        assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, task))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from status_approval_requests where id=?", String.class, request))
                .isEqualTo("CANCELLED");
    }
}
