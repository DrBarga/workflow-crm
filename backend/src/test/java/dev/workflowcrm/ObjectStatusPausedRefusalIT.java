package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusPausedRefusalIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "FOUND",
                "IN_PROGRESS",
                "IC_PREPARATION",
                "COMMERCIAL_TERMS_APPROVAL",
                "CONTRACT_APPROVAL",
                "CONTRACT_SIGNED",
                "OPENED"
            })
    void refusalPermissionUsesStageBeforePause(String previous) throws Exception {
        state("PAUSED");
        jdbc.update("update objects set status_before_pause_id=? where id=?", statusId(previous), object);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(contract, body).andExpect(status().isForbidden());
        change(admin, body).andExpect(status().isForbidden());
        boolean early = java.util.Set.of("FOUND", "IN_PROGRESS").contains(previous);
        if (early) change(manager, body).andExpect(status().isOk());
        else {
            change(manager, body).andExpect(status().isUnprocessableEntity());
            assertThat(state()).isEqualTo("PAUSED");
            change(lead, body).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject(
                        "select status_before_refusal_id from objects where id=?", java.util.UUID.class, object))
                .isEqualTo(statusId(previous));
    }
}
