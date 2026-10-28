package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaskTemplateTransitionIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "INACTIVE", "EMPTY", "UNMATCHED_TYPE"})
    void missingTemplateChangesStatusWithoutTasksAndPublishesOneAdministratorEvent(String state) throws Exception {
        if (!"ABSENT".equals(state)) {
            UUID template = template("IN_PROGRESS", null);
            switch (state) {
                case "INACTIVE" -> jdbc.update("update task_templates set is_active=false where id=?", template);
                case "EMPTY" -> jdbc.update("delete from task_template_items where template_id=?", template);
                case "UNMATCHED_TYPE" -> {
                    jdbc.update("update task_templates set contract_type='PRELIMINARY' where id=?", template);
                    jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
                }
                default -> throw new IllegalArgumentException(state);
            }
        }
        var command = command("IN_PROGRESS");
        var result = response(change(manager, command).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(result.path("confirmationToken").isNull()).isTrue();
        assertThat(result.path("tasks")).isEmpty();
        assertThat(state()).isEqualTo("IN_PROGRESS");
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='TASK_TEMPLATE_MISSING'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_audit where event_type='TASK_TEMPLATE_MISSING'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=? and event_type='OBJECT_TASK_TEMPLATE_MISSING'",
                        Integer.class,
                        admin))
                .isEqualTo(1);
        change(manager, command).andExpect(status().isConflict());
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isEqualTo(1);
    }

    @Test
    void fallsBackToGeneralTemplateWhenTypedPairIsMissing() throws Exception {
        template("IN_PROGRESS", null);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        var command = command("IN_PROGRESS");
        var preview = response(change(manager, command).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(preview.path("tasks")).hasSize(1);
        command.put("confirmationToken", preview.path("confirmationToken").asText());
        change(manager, command)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
    }

    @Test
    void ambiguousConfigurationRejectsTransitionWithoutAnyPartialChanges() throws Exception {
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        UUID normalized = template("IN_PROGRESS", null);
        jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), normalized);
        template("IN_PROGRESS", "PRIMARY");
        change(manager, command("IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TASK_TEMPLATE_CONFIGURATION_ERROR"));
        assertThat(state()).isEqualTo("FOUND");
        assertThat(version()).isZero();
        for (String table : java.util.List.of("task_sets", "tasks", "object_history"))
            assertThat(count(table)).isZero();
        assertThat(events("OBJECT_STATUS_CHANGED")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
    }

    @Test
    void failedOutboxWriteRollsBackMissingTemplateTransitionAndNotification() throws Exception {
        jdbc.execute(
                "alter table event_outbox add constraint ck_t5806_reject_missing_event check(event_type<>'OBJECT_TASK_TEMPLATE_MISSING')");
        try {
            change(manager, command("IN_PROGRESS")).andExpect(status().isInternalServerError());
            assertThat(state()).isEqualTo("FOUND");
            assertThat(version()).isZero();
            assertThat(count("object_history")).isZero();
            assertThat(events("OBJECT_STATUS_CHANGED")).isZero();
            assertThat(jdbc.queryForObject(
                            "select count(*) from user_notifications where event_type='OBJECT_TASK_TEMPLATE_MISSING'",
                            Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("alter table event_outbox drop constraint ck_t5806_reject_missing_event");
        }
    }
}
