package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusExistingSetIT extends ObjectStatusTaskSetsSupport {
    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "COMPLETED", "CANCELLED"})
    void onlyActiveTargetSetSuppressesPreviewAndGeneration(String state) throws Exception {
        state("REFUSED");
        assignedTask(set("IN_PROGRESS", state), "IN_PROGRESS", "NEW");
        template("IN_PROGRESS", null);
        var before = taskSnapshot();
        var result = response(change(lead, command("IN_PROGRESS")).andExpect(status().isOk()));
        boolean active = "ACTIVE".equals(state);
        assertThat(result.path("saved").asBoolean()).isEqualTo(active);
        assertThat(result.path("confirmationToken").isNull()).isEqualTo(active);
        assertThat(result.path("tasks")).hasSize(active ? 0 : 1);
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
        if (!active) {
            var confirmation = command("IN_PROGRESS");
            confirmation.put(
                    "confirmationToken", result.path("confirmationToken").asText());
            change(lead, confirmation).andExpect(status().isOk());
            assertThat(count("task_sets")).isEqualTo(2);
            assertThat(events("TASK_GENERATION_REQUESTED")).isEqualTo(1);
        }
    }

    @Test
    void existingSetSkipsChangedInvalidTemplateAndMissingTemplateAlert() throws Exception {
        state("REFUSED");
        set("IN_PROGRESS", "ACTIVE");
        var template = template("IN_PROGRESS", null);
        jdbc.update("update task_template_items set assignee_id=? where template_id=?", colleague, template);
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        change(lead, command("IN_PROGRESS")).andExpect(status().isOk());
        state("REFUSED");
        jdbc.update("update task_templates set is_active=false where id=?", template);
        change(lead, command("IN_PROGRESS")).andExpect(status().isOk());
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
        assertThat(count("task_sets")).isEqualTo(1);
    }

    @Test
    void approvalReusesActiveTargetSetWithoutPreview() throws Exception {
        set("IC_PREPARATION", "ACTIVE");
        template("IC_PREPARATION", null);
        var result = response(change(manager, command("IC_PREPARATION")).andExpect(status().isOk()));
        var request = java.util.UUID.fromString(result.path("approvalRequestId").asText());
        var approved = response(approval(lead, request, "approve", Map.of()).andExpect(status().isOk()));
        assertThat(approved.path("saved").asBoolean()).isTrue();
        assertThat(approved.path("confirmationToken").isNull()).isTrue();
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
    }

    @Test
    void milestoneReusesActiveTargetSet() throws Exception {
        state("CONTRACT_SIGNED");
        jdbc.update("update objects set contract_signed_date='2026-10-01' where id=?", object);
        assignedTask(set("OPENED", "ACTIVE"), "OPENED", "NEW");
        template("OPENED", null);
        var result = response(milestones(lead, Map.of("version", version(), "openingDate", "2026-10-15"))
                .andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(result.path("confirmationToken").isNull()).isTrue();
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
    }
}
