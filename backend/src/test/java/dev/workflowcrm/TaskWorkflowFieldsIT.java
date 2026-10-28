package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TaskWorkflowFieldsIT extends ObjectStatusTasksSupport {
    @Test
    void confirmedWorkflowSettingsAreFrozenAndNewSettingsCreateAnotherRevision() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID recipientRole = jdbc.queryForObject("select id from roles where code='REGIONAL_MANAGER'", UUID.class);
        jdbc.update(
                "update task_template_items set needs_review=false,result_recipient_role_id=?,escalation_days=2 where template_id=?",
                recipientRole,
                template);
        enter();
        UUID oldVersion = jdbc.queryForObject("select template_version_id from tasks", UUID.class);
        assertThat(jdbc.queryForMap("select author_type,author_id,needs_review,result_recipient_id from tasks"))
                .containsEntry("author_type", "SYSTEM")
                .containsEntry("author_id", manager)
                .containsEntry("needs_review", false)
                .containsEntry("result_recipient_id", lead);
        assertThat(jdbc.queryForMap("select needs_review,result_recipient_role_id,escalation_days from template_items"))
                .containsEntry("needs_review", false)
                .containsEntry("result_recipient_role_id", recipientRole)
                .containsEntry("escalation_days", 2);
        var original = jdbc.queryForList(
                "select to_jsonb(i)::text from template_items i where template_version_id=?", String.class, oldVersion);
        jdbc.update(
                "update task_template_items set needs_review=true,result_recipient_role_id=null,escalation_days=5 where template_id=?",
                template);
        change(lead, command("FOUND")).andExpect(status().isOk());
        enter();
        assertThat(count("task_template_versions")).isEqualTo(2);
        assertThat(jdbc.queryForList(
                        "select to_jsonb(i)::text from template_items i where template_version_id=?",
                        String.class,
                        oldVersion))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("select needs_review from tasks where status='IN_PROGRESS'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select result_recipient_id from tasks where template_version_id=?", UUID.class, oldVersion))
                .isEqualTo(lead);
        assertThat(jdbc.queryForObject("select result_recipient_id from tasks where status='IN_PROGRESS'", UUID.class))
                .isNull();
    }

    @Test
    void changingReviewSettingsInvalidatesPreviousConfirmation() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        var preview = response(change(manager, body).andExpect(status().isOk()));
        jdbc.update("update task_template_items set needs_review=false where template_id=?", template);
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        change(manager, body).andExpect(status().isConflict());
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
    }

    @Test
    void manualTaskCanKeepRecipientWithoutTemplateOrSetAndRejectsInvalidReferences() {
        UUID manual = task("NEW");
        jdbc.update("update tasks set needs_review=false,result_recipient_id=? where id=?", lead, manual);
        assertThat(jdbc.queryForMap(
                        "select author_type,author_id,needs_review,result_recipient_id,source_item_id,task_set_id from tasks where id=?",
                        manual))
                .containsEntry("author_type", "USER")
                .containsEntry("author_id", manager)
                .containsEntry("needs_review", false)
                .containsEntry("result_recipient_id", lead)
                .containsEntry("source_item_id", null)
                .containsEntry("task_set_id", null);
        assertThatThrownBy(() ->
                        jdbc.update("update tasks set result_recipient_id=? where id=?", UUID.randomUUID(), manual))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tasks set author_type='UNKNOWN' where id=?", manual))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from users where id=?", lead))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void escalationRangeAndRecipientRoleAreCheckedInDraftAndFrozenItems() throws Exception {
        template("IN_PROGRESS", null);
        enter();
        for (String table : java.util.List.of("task_template_items", "template_items")) {
            assertThatThrownBy(() -> jdbc.update("update " + table + " set escalation_days=-1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update " + table + " set escalation_days=3651"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(
                            () -> jdbc.update("update " + table + " set result_recipient_role_id=?", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("update " + table + " set escalation_days=0");
        }
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
