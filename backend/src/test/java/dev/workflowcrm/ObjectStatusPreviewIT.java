package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ObjectStatusPreviewIT extends ObjectStatusTasksSupport {
    @Test
    void signingAndOpeningThroughCardRequireTaskPreview() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        template("CONTRACT_SIGNED", "PRIMARY");
        var signing = new java.util.LinkedHashMap<String, Object>();
        signing.put("version", version());
        signing.put("contractSignedDate", "2026-10-04");
        signing.put("contractTypeId", contractType());
        var preview = response(milestones(lead, signing).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        signing.put("confirmationToken", preview.path("confirmationToken").asText());
        milestones(lead, signing).andExpect(status().isOk());
        jdbc.update("update tasks set status='DONE' where object_id=?", object);
        template("OPENED", "PRIMARY");
        var opening = new java.util.LinkedHashMap<String, Object>();
        opening.put("version", version());
        opening.put("openingDate", "2026-10-05");
        preview = response(milestones(director, opening).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        opening.put("confirmationToken", preview.path("confirmationToken").asText());
        milestones(director, opening).andExpect(status().isOk());
        assertThat(state()).isEqualTo("OPENED");
        assertThat(taskSnapshot()).hasSize(5);
    }

    @Test
    void previewMustBeConfirmedAndCreatesOneSetAtomically() throws Exception {
        template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        var preview = response(change(manager, body).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(preview.path("tasks")).hasSize(1);
        assertThat(state()).isEqualTo("FOUND");
        assertThat(taskSnapshot()).isEmpty();
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        assertThat(response(change(manager, body).andExpect(status().isOk()))
                        .path("saved")
                        .asBoolean())
                .isTrue();
        assertThat(taskSnapshot()).hasSize(1);
        assertThat(events("TASK_GENERATION_REQUESTED")).isEqualTo(1);
        change(manager, body).andExpect(status().isConflict());
        assertThat(taskSnapshot()).hasSize(1);
    }

    @Test
    void changingTemplateInvalidatesPreview() throws Exception {
        var id = template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        body.put(
                "confirmationToken",
                response(change(manager, body)).path("confirmationToken").asText());
        jdbc.update("update task_template_items set name='Інша задача',version=version+1 where template_id=?", id);
        change(manager, body).andExpect(status().isConflict());
        assertThat(state()).isEqualTo("FOUND");
        assertThat(taskSnapshot()).isEmpty();
    }

    @Test
    void absentOrInactiveTemplateChangesStatusAndAlertsAdministrator() throws Exception {
        var id = template("IN_PROGRESS", null);
        jdbc.update("update task_templates set is_active=false where id=?", id);
        var result = response(change(manager, command("IN_PROGRESS")).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(result.path("confirmationToken").isNull()).isTrue();
        assertThat(taskSnapshot()).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='TASK_TEMPLATE_MISSING'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=? and event_type='OBJECT_TASK_TEMPLATE_MISSING'",
                        Integer.class,
                        admin))
                .isEqualTo(1);
    }
}
