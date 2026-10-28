package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class IcDecisionSecurityIT extends IcDecisionTestSupport {
    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        var body = json.writeValueAsBytes(decision("APPROVED"));
        mvc.perform(patch("/api/objects/" + object + "/ic-decision")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/objects/" + object + "/ic-decision")
                        .cookie(cookie(lead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        assertThat(count("object_history")).isZero();
    }

    @Test
    void confirmationIsBoundToActorAndRejectsInvalidTokens() throws Exception {
        template();
        var body = decision("APPROVED");
        String token = response(decide(lead, body)).path("confirmationToken").asText();
        body.put("confirmationToken", token);
        decide(director, body).andExpect(status().isConflict());
        body.put("confirmationToken", "0".repeat(64));
        decide(lead, body).andExpect(status().isConflict());
        body.put("confirmationToken", "bad");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("IC_PREPARATION");
    }

    @Test
    void historyFailureRollsBackDecisionCancellationAndNewTasks() throws Exception {
        template();
        var old = task("IN_PROGRESS");
        var body = decision("APPROVED");
        body.put(
                "confirmationToken",
                response(decide(lead, body)).path("confirmationToken").asText());
        jdbc.execute("alter table object_history add constraint ic_test_history_failure check(field_name <> 'STATUS')");
        try {
            decide(lead, body).andExpect(status().isInternalServerError());
            assertThat(state()).isEqualTo("IC_PREPARATION");
            assertThat(count("tasks")).isEqualTo(1);
            assertThat(count("task_history")).isZero();
            assertThat(count("object_history")).isZero();
            assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, old))
                    .isEqualTo("IN_PROGRESS");
        } finally {
            jdbc.execute("alter table object_history drop constraint ic_test_history_failure");
        }
    }
}
