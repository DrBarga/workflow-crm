package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectReassignmentTasksIT extends ObjectReassignmentTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"})
    void transfersOnlyUnfinishedTasksPreservingTheirData(String taskStatus) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, taskStatus);
        String before = taskSnapshot(task);
        var result = response(reassign(lead, object, colleague).andExpect(status().isOk()));
        boolean unfinished = !Set.of("DONE", "CANCELLED").contains(taskStatus);
        assertThat(assignee(task)).isEqualTo(unfinished ? colleague : manager);
        assertThat(transferred(task)).isEqualTo(unfinished);
        assertThat(taskSnapshot(task)).isEqualTo(before);
        assertThat(count("task_assignment_history")).isEqualTo(unfinished ? 1 : 0);
        assertThat(result.path("transferredTasks").size()).isEqualTo(unfinished ? 1 : 0);
        if (unfinished) {
            var row = result.path("transferredTasks").get(0);
            assertThat(row.path("label").asText()).isEqualTo("задача перейшла від попередника");
            assertThat(row.path("version").asLong()).isEqualTo(1);
            assertThat(row.path("previousAssigneeId").asText()).isEqualTo(manager.toString());
        }
    }

    @Test
    void otherExecutorsAndObjectsAreUntouched() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID other = add("Інший об’єкт", manager, region);
        UUID transferredTask = task(object, manager, "NEW");
        UUID leadTask = task(object, lead, "IN_PROGRESS");
        UUID foreign = task(other, manager, "NEW");
        reassign(director, object, colleague).andExpect(status().isOk());
        assertThat(assignee(transferredTask)).isEqualTo(colleague);
        assertThat(assignee(leadTask)).isEqualTo(lead);
        assertThat(assignee(foreign)).isEqualTo(manager);
        assertThat(owner(other)).isEqualTo(manager);
        assertThat(transferred(leadTask)).isFalse();
        assertThat(transferred(foreign)).isFalse();
    }

    @Test
    void repeatedReassignmentPreservesEveryPreviousExecutor() throws Exception {
        UUID next = UUID.randomUUID();
        user(next, "MANAGER", region);
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "NEW");
        reassign(lead, object, colleague).andExpect(status().isOk());
        reassign(director, object, next).andExpect(status().isOk());
        assertThat(assignee(task)).isEqualTo(next);
        assertThat(transferred(task)).isTrue();
        assertThat(jdbc.queryForList(
                        "select previous_assignee_id from task_assignment_history order by task_version", UUID.class))
                .containsExactly(manager, colleague);
        assertThat(jdbc.queryForObject("select version from tasks where id=?", Long.class, task))
                .isEqualTo(2);
    }
}
