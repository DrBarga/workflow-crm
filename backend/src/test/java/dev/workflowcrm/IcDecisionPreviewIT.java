package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IcDecisionPreviewIT extends IcDecisionTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "APPROVED_WITH_CONDITIONS"})
    void previewsThenConfirmsExactlyOnce(String value) throws Exception {
        template();
        for (String taskState :
                java.util.List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"))
            task(taskState);
        var before = taskSnapshot();
        var body = decision(value);
        var preview = response(decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.tasks[0].assigneeId").value(manager.toString()))
                .andExpect(jsonPath("$.tasks[0].assigneeName").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0].deadline").value("2026-10-08")));
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("object_history")).isZero();
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(count("task_history")).isZero();
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("COMMERCIAL_TERMS_APPROVAL");
        assertThat(count("tasks")).isEqualTo(7);
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(count("task_history")).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks t join object_statuses s on s.id=t.source_status_id where s.code='COMMERCIAL_TERMS_APPROVAL' and t.status='IN_PROGRESS'",
                        Integer.class))
                .isEqualTo(1);
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(count("tasks")).isEqualTo(7);
        assertThat(decisionHistoryCount()).isEqualTo(2);
    }

    @Test
    void changedInputOrTemplateRequiresFreshPreview() throws Exception {
        var template = template();
        var body = decision("APPROVED");
        var preview = response(decide(lead, body));
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        body.put("date", "2026-10-06");
        decide(lead, body).andExpect(status().isConflict());
        body.put("date", "2026-10-05");
        jdbc.update("update task_template_items set deadline_offset_days=5 where template_id=?", template);
        decide(lead, body).andExpect(status().isConflict());
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("tasks")).isZero();
    }
}
