package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IcDecisionTasksIT extends IcDecisionTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "APPROVED_WITH_CONDITIONS"})
    void approvedOutcomesPreserveEveryTask(String value) throws Exception {
        for (String taskState :
                java.util.List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"))
            task(taskState);
        var before = taskSnapshot();
        decide(lead, decision(value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("REFUSED".equals(value) ? "REFUSED" : "COMMERCIAL_TERMS_APPROVAL");
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(count("task_history")).isZero();
        assertThat(decisionHistoryCount()).isEqualTo(2);
    }

    @Test
    void respectsConfiguredBlockAction() throws Exception {
        var task = task("IN_PROGRESS");
        var set = java.util.UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id) select ?,?,status_id from objects where id=?",
                set,
                object,
                object);
        jdbc.update("update tasks set task_set_id=? where id=?", set, task);
        jdbc.update("update object_status_transitions set unfinished_task_action='BLOCK' where code='IC_APPROVED'");
        try {
            decide(lead, decision("APPROVED")).andExpect(status().isUnprocessableEntity());
            assertThat(state()).isEqualTo("IC_PREPARATION");
            assertThat(count("object_history")).isZero();
            assertThat(count("task_history")).isZero();
        } finally {
            jdbc.update("update object_status_transitions set unfinished_task_action='NONE' where code='IC_APPROVED'");
        }
    }

    @Test
    void inactiveAssigneeReturnsManualChoiceWithoutSaving() throws Exception {
        template();
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.confirmationToken").isEmpty())
                .andExpect(jsonPath("$.tasks[0].assignment.selectionRequired").value(true))
                .andExpect(jsonPath("$.tasks[0].assignment.issue").value("INACTIVE"));
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("object_history")).isZero();
    }

    @Test
    void emptyTemplateAllowsDecisionAndPublishesMissingTemplateEvent() throws Exception {
        var template = template();
        jdbc.update("delete from task_template_items where template_id=?", template);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("COMMERCIAL_TERMS_APPROVAL");
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from event_outbox where event_type='OBJECT_TASK_TEMPLATE_MISSING'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void refusalDoesNotGenerateCommercialTasksEvenWithConfiguredTemplate() throws Exception {
        template();
        decide(lead, decision("REFUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isZero();
    }

    @Test
    void selectsTemplateByContractTypeAndDoesNotUseUnrelatedTemplates() throws Exception {
        var id = template();
        jdbc.update("update task_templates set contract_type='LEASE' where id=?", id);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isZero();
    }
}
