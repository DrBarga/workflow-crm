package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TaskAssigneeChangeIT extends TaskAssignmentTestSupport {
    @Test
    void authorizedChangeWritesHistoryAndEventOnceAndRetainsTaskState() throws Exception {
        UUID task = task("PAUSED");
        assign(lead, task, colleague, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        var row = jdbc.queryForMap(
                "select assignee_id,version,status,transferred_from_predecessor from tasks where id=?", task);
        assertThat(row.get("assignee_id")).isEqualTo(colleague);
        assertThat(row.get("status")).isEqualTo("PAUSED");
        assertThat(row.get("transferred_from_predecessor")).isEqualTo(false);
        var history = jdbc.queryForMap(
                "select actor_id,previous_assignee_id,new_assignee_id,task_version from task_assignment_history where task_id=?",
                task);
        assertThat(history.get("actor_id")).isEqualTo(lead);
        assertThat(history.get("previous_assignee_id")).isEqualTo(manager);
        assertThat(history.get("new_assignee_id")).isEqualTo(colleague);
        assertThat(history.get("task_version")).isEqualTo(1L);
        assertThat(events("TASK_REASSIGNED")).isEqualTo(1);
        assign(director, task, colleague, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
        assertThat(count("task_assignment_history")).isEqualTo(1);
        assign(lead, task, manager, 0).andExpect(status().isConflict());
        assertThat(count("task_assignment_history")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "CEO", "SUPERADMIN", "CONTRACT_MANAGER", "FOREIGN_LEAD", "DISABLED_LEAD"})
    void manualChangesRequireRegionalLeadOrDirector(String role) throws Exception {
        UUID task = task("IN_PROGRESS");
        UUID actor = role.equals("FOREIGN_LEAD") ? foreignLead : role.equals("DISABLED_LEAD") ? lead : actor(role);
        if (role.equals("DISABLED_LEAD")) jdbc.update("update users set status='DISABLED' where id=?", lead);
        assign(actor, task, colleague, 0).andExpect(status().isForbidden());
        assertThat(count("task_assignment_history")).isZero();
        assertThat(jdbc.queryForObject("select assignee_id from tasks where id=?", UUID.class, task))
                .isEqualTo(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FOREIGN", "DISABLED", "UNKNOWN", "DONE", "CANCELLED"})
    void invalidTargetsOrClosedTasksDoNotWriteAnything(String variant) throws Exception {
        UUID task = task(List.of("DONE", "CANCELLED").contains(variant) ? variant : "IN_PROGRESS");
        UUID target = variant.equals("FOREIGN") ? outsider : variant.equals("UNKNOWN") ? UUID.randomUUID() : colleague;
        if (variant.equals("DISABLED")) jdbc.update("update users set status='DISABLED' where id=?", colleague);
        assign(director, task, target, 0).andExpect(status().isUnprocessableEntity());
        assertThat(count("task_assignment_history")).isZero();
        assertThat(events("TASK_REASSIGNED")).isZero();
    }

    @Test
    void replacementUsesImmutableTemplateRoleAfterDraftIsChanged() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID task = generate("IN_PROGRESS", manager, command("IN_PROGRESS"));
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        jdbc.update("update task_template_items set role_id=? where id=?", role("CONTRACT_MANAGER"), item);
        var options = response(mvc.perform(get("/api/objects/{id}/tasks/{task}/assignees", object, task)
                        .cookie(cookie(lead)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(options.findValuesAsText("id")).containsExactlyInAnyOrder(lead.toString(), second.toString());
        assign(director, task, contract, 0).andExpect(status().isUnprocessableEntity());
        assign(lead, task, second, 0).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select manager_id from objects where id=?", UUID.class, object))
                .isEqualTo(manager);
    }

    @Test
    void ownerBoundTemplateCannotBeAssignedToColleague() throws Exception {
        roleItem("IN_PROGRESS", "NETWORK_ROLE", "MANAGER");
        UUID task = generate("IN_PROGRESS", manager, command("IN_PROGRESS"));
        assign(director, task, colleague, 0).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void taskMustBelongToRequestedObjectAndRequestMustBeAuthenticated() throws Exception {
        UUID task = task("IN_PROGRESS");
        assign(lead, UUID.randomUUID(), colleague, 0).andExpect(status().isNotFound());
        mvc.perform(patch("/api/objects/{id}/tasks/{task}/assignee", object, task)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + colleague + "\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/objects/{id}/tasks/{task}/assignee", object, task)
                        .cookie(cookie(lead), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"invalid\",\"version\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void concurrentEditorsCannotOverwriteAssignment() throws Exception {
        UUID task = task("IN_PROGRESS");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                return assign(lead, task, colleague, 0)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return assign(director, task, lead, 0).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(count("task_assignment_history")).isEqualTo(1);
        assertThat(events("TASK_REASSIGNED")).isEqualTo(1);
    }

    private ResultActions assign(UUID actor, UUID task, UUID assignee, long version) throws Exception {
        return mvc.perform(patch("/api/objects/{id}/tasks/{task}/assignee", object, task)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("assigneeId", assignee.toString(), "version", version))));
    }
}
