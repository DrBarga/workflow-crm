package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskExecutionAccessIT extends TaskDependencyTestSupport {
    @Test
    void onlyActiveAssigneeInAllowedRegionCanCompleteTask() throws Exception {
        UUID task = node(null, "IN_PROGRESS");
        for (UUID actor : new UUID[] {colleague, lead, director, contract, admin, ceo, outsider})
            complete(actor, task, 0).andExpect(status().isForbidden());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        complete(manager, task, 0).andExpect(status().isForbidden());
        assertThat(taskVersion(task)).isZero();
    }

    @Test
    void assigneeCannotActAfterRegionalAssignmentIsRemoved() throws Exception {
        UUID task = node(null, "IN_PROGRESS");
        jdbc.update("delete from user_regions where user_id=?", manager);
        complete(manager, task, 0).andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadCsrfAndForeignTaskAreRejected() throws Exception {
        UUID task = node(null, "IN_PROGRESS");
        complete(manager, UUID.randomUUID(), 0).andExpect(status().isNotFound());
        mvc.perform(post("/api/objects/{id}/tasks/{task}/complete", object, task)
                        .cookie(cookie(manager))
                        .contentType("application/json")
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/objects/{id}/tasks/{task}/complete", object, task)
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"version\":0,\"status\":\"DONE\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/objects/{id}/tasks/{task}/complete", object, task)
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{\"version\":-1}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(taskVersion(task)).isZero();
    }

    @Test
    void objectPauseAndTaskPauseBlockCompletion() throws Exception {
        UUID first = node(null, "IN_PROGRESS"), second = node(null, "PAUSED");
        complete(manager, second, 0).andExpect(status().isUnprocessableEntity());
        state("PAUSED");
        complete(manager, first, 0).andExpect(status().isUnprocessableEntity());
        assertThat(taskVersion(first)).isZero();
    }
}
