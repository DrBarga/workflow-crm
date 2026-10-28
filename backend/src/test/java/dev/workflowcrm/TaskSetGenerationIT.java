package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskSetGenerationIT extends ObjectStatusTasksSupport {
    @Test
    void previewHasNoSideEffectsAndRepeatConfirmationDoesNotCreateAnotherSet() throws Exception {
        template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        var preview = response(change(manager, body).andExpect(status().isOk()));
        assertThat(count("task_sets")).isZero();
        assertThat(count("task_template_versions")).isZero();
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        change(manager, body).andExpect(status().isOk());
        change(manager, body).andExpect(status().isConflict());
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where source_item_id is not null and template_version_id is not null",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void editingDraftDoesNotChangeOldSourceAndReentryCreatesAnotherVersion() throws Exception {
        var template = template("IN_PROGRESS", null);
        enter();
        UUID oldVersion = jdbc.queryForObject("select template_version_id from tasks", UUID.class);
        var original = jdbc.queryForList("select to_jsonb(i)::text from template_items i", String.class);
        jdbc.update(
                "update task_template_items set name='Нове завдання',deadline_offset_days=7 where template_id=?",
                template);
        change(lead, command("FOUND")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select state from task_sets", String.class))
                .isEqualTo("CANCELLED");
        enter();
        assertThat(count("task_sets")).isEqualTo(2);
        assertThat(count("task_template_versions")).isEqualTo(2);
        assertThat(jdbc.queryForList(
                        "select to_jsonb(i)::text from template_items i where template_version_id=?",
                        String.class,
                        oldVersion))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("select name from tasks where status='IN_PROGRESS'", String.class))
                .isEqualTo("Нове завдання");
        assertThat(jdbc.queryForObject("select name from tasks where status='CANCELLED'", String.class))
                .isEqualTo("Перевірити об’єкт");
    }

    @Test
    void unchangedDefinitionReusesVersionButNotTaskSet() throws Exception {
        template("IN_PROGRESS", null);
        enter();
        change(lead, command("FOUND")).andExpect(status().isOk());
        enter();
        assertThat(count("task_sets")).isEqualTo(2);
        assertThat(count("task_template_versions")).isEqualTo(1);
    }

    private void enter() throws Exception {
        var body = command("IN_PROGRESS");
        body.put(
                "confirmationToken",
                response(change(manager, body).andExpect(status().isOk()))
                        .path("confirmationToken")
                        .asText());
        change(manager, body).andExpect(status().isOk());
    }
}
