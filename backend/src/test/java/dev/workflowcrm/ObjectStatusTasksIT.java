package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusTasksIT extends ObjectStatusTasksSupport {
    @Test
    void forwardTransitionPreservesUnfinishedTasks() throws Exception {
        for (String value : List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"))
            task(value);
        var before = taskSnapshot();
        var result = response(change(manager, command("IN_PROGRESS")).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(state()).isEqualTo("IN_PROGRESS");
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(events("TASK_CANCELLED")).isZero();
    }

    @Test
    void refusalCancelsUnfinishedTasksFromAllStagesAndNotifiesAssignee() throws Exception {
        state("IN_PROGRESS");
        for (String value : List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"))
            task(value);
        jdbc.update("update tasks set source_status_id=? where object_id=?", statusId("FOUND"), object);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(manager, body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from tasks where status='CANCELLED'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from task_history", Integer.class))
                .isEqualTo(4);
        assertThat(events("TASK_CANCELLED")).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=? and event_type='TASK_CANCELLED'",
                        Integer.class,
                        manager))
                .isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DONE", "CANCELLED", "NEW"})
    void anyHistoricalTaskPreventsManagerReturnToFound(String taskStatus) throws Exception {
        state("IN_PROGRESS");
        task(taskStatus);
        change(manager, command("FOUND")).andExpect(status().isUnprocessableEntity());
        change(lead, command("FOUND")).andExpect(status().isOk());
    }

    @Test
    void pauseAndResumePreserveTasksAndNeverGenerateAnotherSet() throws Exception {
        state("IN_PROGRESS");
        task("IN_PROGRESS");
        template("IN_PROGRESS", null);
        template("PAUSED", null);
        var before = taskSnapshot();
        change(manager, command("PAUSED")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status_before_pause_id from objects where id=?", UUID.class, object))
                .isEqualTo(statusId("IN_PROGRESS"));
        change(manager, command("IN_PROGRESS")).andExpect(status().isOk());
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
    }
}
