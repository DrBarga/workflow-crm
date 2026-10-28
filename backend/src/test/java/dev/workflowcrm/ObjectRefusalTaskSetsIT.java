package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectRefusalTaskSetsIT extends ObjectStatusTasksSupport {
    @Test
    void cancelAllClosesActiveSetsFromDifferentStagesAndPreservesCompletedSet() throws Exception {
        state("IN_PROGRESS");
        UUID current = task("IN_PROGRESS"), previous = task("PAUSED"), completed = task("DONE");
        jdbc.update("update tasks set source_status_id=? where id=?", statusId("FOUND"), previous);
        UUID currentSet = set(current, "IN_PROGRESS", "ACTIVE"), previousSet = set(previous, "FOUND", "ACTIVE");
        UUID completedSet = set(completed, "IN_PROGRESS", "COMPLETED");
        String before =
                jdbc.queryForObject("select to_jsonb(s)::text from task_sets s where id=?", String.class, completedSet);
        var command = command("REFUSED");
        command.put("refusalReasonId", reason());
        change(manager, command).andExpect(status().isOk());
        assertThat(jdbc.queryForList(
                        "select state from task_sets where object_id=? and id<>?", String.class, object, completedSet))
                .containsOnly("CANCELLED")
                .hasSize(2);
        assertThat(jdbc.queryForObject(
                        "select to_jsonb(s)::text from task_sets s where id=?", String.class, completedSet))
                .isEqualTo(before);
        assertThat(jdbc.queryForList(
                        "select status from tasks where task_set_id in (?,?)", String.class, currentSet, previousSet))
                .containsOnly("CANCELLED")
                .hasSize(2);
        assertThat(events("TASK_CANCELLED")).isEqualTo(2);
    }

    private UUID set(UUID task, String source, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state) values (?,?,?,?)",
                id,
                object,
                statusId(source),
                state);
        jdbc.update("update tasks set task_set_id=? where id=?", id, task);
        return id;
    }
}
