package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectRefusalPolicyIT extends ObjectStatusTasksSupport {
    @Test
    void taskActionComesFromTransitionInsteadOfTargetStatus() throws Exception {
        task("IN_PROGRESS");
        var tasks = taskSnapshot();
        UUID rule = jdbc.queryForObject(
                "select id from object_status_transitions where from_status_id=? and is_refusal_entry and not is_system_only",
                UUID.class,
                statusId("FOUND"));
        String previous = jdbc.queryForObject(
                "select unfinished_task_action from object_status_transitions where id=?", String.class, rule);
        jdbc.update("update object_status_transitions set unfinished_task_action='NONE' where id=?", rule);
        try {
            var body = command("REFUSED");
            body.put("refusalReasonId", reason());
            change(manager, body).andExpect(status().isOk());
            assertThat(state()).isEqualTo("REFUSED");
            assertThat(taskSnapshot()).isEqualTo(tasks);
            assertThat(events("TASK_CANCELLED")).isZero();
        } finally {
            jdbc.update("update object_status_transitions set unfinished_task_action=? where id=?", previous, rule);
        }
    }

    @Test
    void validationRoleChangesErrorClassificationWithoutGrantingPermission() throws Exception {
        state("IC_PREPARATION");
        UUID rule = jdbc.queryForObject(
                "select id from object_status_transitions where from_status_id=? and is_refusal_entry and not is_system_only",
                UUID.class,
                statusId("IC_PREPARATION"));
        UUID role = jdbc.queryForObject("select id from roles where code='MANAGER'", UUID.class);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(manager, body).andExpect(status().isUnprocessableEntity());
        jdbc.update("delete from object_transition_validation_roles where transition_id=? and role_id=?", rule, role);
        try {
            change(manager, body).andExpect(status().isForbidden());
            assertThat(state()).isEqualTo("IC_PREPARATION");
            assertThat(version()).isZero();
        } finally {
            jdbc.update(
                    "insert into object_transition_validation_roles(transition_id,role_id) values (?,?)", rule, role);
        }
    }
}
