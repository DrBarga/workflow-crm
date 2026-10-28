package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusTaskScopeIT extends ObjectStatusTaskSetsSupport {
    @Test
    void backwardStepCancelsOnlyUnfinishedTasksOfActiveSourceSet() throws Exception {
        state("CONTRACT_APPROVAL");
        var active = set("CONTRACT_APPROVAL", "ACTIVE");
        var current = assignedTask(active, "CONTRACT_APPROVAL", "WAITING_APPROVAL");
        var done = assignedTask(active, "CONTRACT_APPROVAL", "DONE");
        var inactive = assignedTask(set("CONTRACT_APPROVAL", "CANCELLED"), "CONTRACT_APPROVAL", "NEW");
        var earlier = assignedTask(set("IN_PROGRESS", "ACTIVE"), "IN_PROGRESS", "PAUSED");
        var legacy = task("NEW");
        change(lead, command("COMMERCIAL_TERMS_APPROVAL")).andExpect(status().isOk());
        assertThat(taskState(current)).isEqualTo("CANCELLED");
        assertThat(taskState(done)).isEqualTo("DONE");
        assertThat(taskState(inactive)).isEqualTo("NEW");
        assertThat(taskState(earlier)).isEqualTo("PAUSED");
        assertThat(taskState(legacy)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("select state from task_sets where id=?", String.class, active))
                .isEqualTo("CANCELLED");
        assertThat(events("TASK_CANCELLED")).isEqualTo(1);
        assertThat(count("task_history")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "PAUSED"})
    void returnToFoundCancelsAllStagesAndSets(String source) throws Exception {
        state(source);
        var first = assignedTask(set("IN_PROGRESS", "ACTIVE"), "IN_PROGRESS", "NEW");
        var second = assignedTask(set("CONTRACT_APPROVAL", "ACTIVE"), "CONTRACT_APPROVAL", "PAUSED");
        var legacy = task("IN_PROGRESS");
        change(manager, command("FOUND")).andExpect(status().isUnprocessableEntity());
        change(lead, command("FOUND")).andExpect(status().isOk());
        for (var id : java.util.List.of(first, second, legacy))
            assertThat(taskState(id)).isEqualTo("CANCELLED");
        assertThat(events("TASK_CANCELLED")).isEqualTo(3);
        assertThat(jdbc.queryForList("select state from task_sets where object_id=?", String.class, object))
                .containsOnly("CANCELLED");
    }

    @Test
    void historicalEmptySetAlsoPreventsManagerReturn() throws Exception {
        state("IN_PROGRESS");
        set("IN_PROGRESS", "COMPLETED");
        change(manager, command("FOUND")).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("IN_PROGRESS");
        change(lead, command("FOUND")).andExpect(status().isOk());
    }
}
