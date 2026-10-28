package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class TaskManualPauseModelIT extends ObjectStatusTasksSupport {
    @Test
    void manualPauseIsIndependentOfOtherTaskFieldsAndCancellationClearsIt() throws Exception {
        UUID id = task("PAUSED");
        jdbc.update("update tasks set manually_paused=true where id=?", id);
        jdbc.update("update tasks set name='Revised paused task',version=version+1 where id=?", id);
        assertThat(jdbc.queryForObject("select manually_paused from tasks where id=?", Boolean.class, id))
                .isTrue();
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(manager, body).andExpect(status().isOk());
        var task = jdbc.queryForMap("select status,manually_paused,version from tasks where id=?", id);
        assertThat(task.get("status")).isEqualTo("CANCELLED");
        assertThat(task.get("manually_paused")).isEqualTo(false);
        assertThat(task.get("version")).isEqualTo(2L);
        assertThat(count("task_history")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "WAITING_APPROVAL", "DONE", "CANCELLED"})
    void manualPauseRequiresPausedStatus(String status) {
        UUID id = task(status);
        assertThatThrownBy(() -> jdbc.update("update tasks set manually_paused=true where id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select manually_paused from tasks where id=?", Boolean.class, id))
                .isFalse();
    }

    @Test
    void manualTaskKeepsEmptySourceAndGeneratedTaskDefaultsToUnpaused() throws Exception {
        UUID manual = task("NEW");
        assertThat(jdbc.queryForMap(
                                "select source_item_id,template_version_id,task_set_id from tasks where id=?", manual)
                        .values())
                .containsOnlyNulls();
        assertThat(jdbc.queryForObject("select author_type from tasks where id=?", String.class, manual))
                .isEqualTo("USER");
        template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        body.put(
                "confirmationToken",
                response(change(manager, body)).path("confirmationToken").asText());
        change(manager, body).andExpect(status().isOk());
        var automatic = jdbc.queryForMap(
                "select author_type,source_item_id,template_version_id,task_set_id,manually_paused from tasks where id<>?",
                manual);
        assertThat(automatic.get("author_type")).isEqualTo("SYSTEM");
        assertThat(automatic.get("manually_paused")).isEqualTo(false);
        for (String field : List.of("source_item_id", "template_version_id", "task_set_id"))
            assertThat(automatic.get(field)).isNotNull();
    }
}
