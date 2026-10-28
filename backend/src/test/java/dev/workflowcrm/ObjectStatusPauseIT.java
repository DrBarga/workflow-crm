package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ObjectStatusPauseIT extends ObjectStatusTasksSupport {
    @Test
    void managerCannotPauseOrResumeLateStage() throws Exception {
        state("IC_PREPARATION");
        change(manager, command("PAUSED")).andExpect(status().isForbidden());
        change(lead, command("PAUSED")).andExpect(status().isOk());
        change(manager, command("IC_PREPARATION")).andExpect(status().isForbidden());
        change(lead, command("COMMERCIAL_TERMS_APPROVAL")).andExpect(status().isUnprocessableEntity());
        change(lead, command("IC_PREPARATION")).andExpect(status().isOk());
    }

    @Test
    void pausedRefusalResumesWithoutReasonAndWithoutCancellingTasks() throws Exception {
        state("IN_PROGRESS");
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(manager, body).andExpect(status().isOk());
        change(lead, command("PAUSED")).andExpect(status().isOk());
        task("NEW");
        var before = taskSnapshot();
        change(lead, command("REFUSED")).andExpect(status().isOk());
        assertThat(state()).isEqualTo("REFUSED");
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                        "select refusal_reason_id from objects where id=?", java.util.UUID.class, object))
                .isNotNull();
    }

    @Test
    void refusingPausedObjectRecordsStageBeforePause() throws Exception {
        state("IC_PREPARATION");
        change(lead, command("PAUSED")).andExpect(status().isOk());
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(director, body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select status_before_refusal_id from objects where id=?", java.util.UUID.class, object))
                .isEqualTo(statusId("IC_PREPARATION"));
        change(manager, command("FOUND")).andExpect(status().isUnprocessableEntity());
    }
}
