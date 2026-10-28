package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrontendObjectTaskApiIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void savedTasksIncludeDependencyAndSetMetadataForPermittedRoles(String role) throws Exception {
        UUID predecessor = task("IN_PROGRESS"), dependent = task("NEW");
        UUID set = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state) values (?,?,?,'ACTIVE')",
                set,
                object,
                statusId("FOUND"));
        jdbc.update("update tasks set task_set_id=? where id in (?,?)", set, predecessor, dependent);
        jdbc.update(
                "insert into task_dependencies(task_id,predecessor_id,object_id) values (?,?,?)",
                dependent,
                predecessor,
                object);
        var page = read(actor(role), "?size=1");
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("hasMore").asBoolean()).isTrue();
        assertThat(page.path("taskSets").get(0).path("id").asText()).isEqualTo(set.toString());
        var tasks = read(actor(role), "?taskSetId=" + set).path("items");
        assertThat(tasks).hasSize(2);
        for (var task : tasks) {
            assertThat(task.path("assignee").path("id").asText()).isEqualTo(manager.toString());
            assertThat(task.path("deadline").asText()).isEqualTo("2026-10-04");
            assertThat(task.path("statusName").asText()).isNotBlank();
            if (task.path("id").asText().equals(dependent.toString())) {
                assertThat(task.path("predecessors")).hasSize(1);
                assertThat(task.path("predecessors").get(0).path("id").asText()).isEqualTo(predecessor.toString());
            }
        }
        assertThat(read(actor(role), "?taskSetId=" + UUID.randomUUID()).path("items"))
                .isEmpty();
    }

    @Test
    void taskListDoesNotExtendForeignObjectVisibilityOrWritePermissions() throws Exception {
        task("IN_PROGRESS");
        for (UUID actor : List.of(colleague, outsider, foreignLead))
            mvc.perform(get("/api/objects/" + object + "/tasks").cookie(cookie(actor)))
                    .andExpect(status().isForbidden());
        assertThat(read(contract, "").path("items")).hasSize(1);
        mvc.perform(get("/api/objects/" + UUID.randomUUID() + "/tasks").cookie(cookie(manager)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/objects/" + object + "/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/objects/" + object + "/tasks")
                        .param("size", "101")
                        .cookie(cookie(manager)))
                .andExpect(status().isUnprocessableEntity());
        jdbc.update(
                "insert into object_deletion_operations(object_id,actor_id,actor_name) values (?,?,'Admin')",
                object,
                admin);
        try {
            mvc.perform(get("/api/objects/" + object + "/tasks").cookie(cookie(manager)))
                    .andExpect(status().isConflict());
        } finally {
            ObjectTestCleanup.erase(
                    jdbc, () -> jdbc.update("delete from object_deletion_operations where object_id=?", object));
        }
    }

    private JsonNode read(UUID actor, String query) throws Exception {
        return response(
                mvc.perform(get("/api/objects/" + object + "/tasks" + query).cookie(cookie(actor)))
                        .andExpect(status().isOk()));
    }
}
