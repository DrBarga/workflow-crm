package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ObjectStatusNotificationsIT extends ObjectStatusTasksSupport {
    @Test
    void notificationsAreReadableOnlyByTheirRecipient() throws Exception {
        state("IN_PROGRESS");
        task("NEW");
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(lead, body).andExpect(status().isOk());
        var mine = response(
                mvc.perform(get("/api/notifications").cookie(cookie(manager))).andExpect(status().isOk()));
        var other = response(
                mvc.perform(get("/api/notifications").cookie(cookie(colleague))).andExpect(status().isOk()));
        assertThat(mine.toString()).contains("TASK_CANCELLED", object.toString());
        assertThat(other).isEmpty();
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        mvc.perform(get("/api/notifications").cookie(cookie(manager))).andExpect(status().isForbidden());
    }
}
