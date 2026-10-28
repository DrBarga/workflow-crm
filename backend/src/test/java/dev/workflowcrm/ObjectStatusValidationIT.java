package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectStatusValidationIT extends ObjectStatusTasksSupport {
    @Test
    void missingReasonAndSystemReasonAreRejected() throws Exception {
        change(manager, command("REFUSED")).andExpect(status().isUnprocessableEntity());
        var body = command("REFUSED");
        body.put(
                "refusalReasonId",
                jdbc.queryForObject("select id from refusal_reasons where is_system_only limit 1", UUID.class));
        change(manager, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void unrelatedFieldUnknownFieldAndStaleVersionAreRejected() throws Exception {
        var body = command("IN_PROGRESS");
        body.put("openingDate", "2026-10-05");
        change(manager, body).andExpect(status().isUnprocessableEntity());
        body = command("IN_PROGRESS");
        body.put("isSystemOnly", true);
        change(manager, body).andExpect(status().isUnprocessableEntity());
        body = command("IN_PROGRESS");
        body.put("version", 99);
        change(manager, body).andExpect(status().isConflict());
        assertThat(version()).isZero();
    }

    @Test
    void unavailableAssigneeRejectsPreviewWithoutSavingObject() throws Exception {
        template("IN_PROGRESS", null);
        jdbc.update("update task_template_items set assignee_id=?", colleague);
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        change(manager, command("IN_PROGRESS")).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("FOUND");
        assertThat(taskSnapshot()).isEmpty();
    }
}
