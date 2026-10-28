package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class TaskDependenciesIT extends TaskDependencyTestSupport {
    @Test
    void predecessorCompletionOpensDependentOnceWithHistoryAndNotification() throws Exception {
        UUID set = set(), first = node(set, "IN_PROGRESS"), second = node(set, "NEW");
        depends(second, first);
        complete(manager, second, 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TASK_DEPENDENCIES_NOT_MET"))
                .andExpect(jsonPath("$.blockingTasks[0].taskId").value(first.toString()));
        assertThat(taskVersion(second)).isZero();
        complete(manager, first, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        assertThat(taskStatus(second)).isEqualTo("IN_PROGRESS");
        assertThat(taskVersion(second)).isEqualTo(1);
        assertThat(events("TASK_STARTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where task_id=? and event_type='TASK_STARTED'",
                        Integer.class,
                        second))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_history where task_id=? and actor_type='SYSTEM' and new_status='IN_PROGRESS'",
                        Integer.class,
                        second))
                .isEqualTo(1);
        recheck();
        recheck();
        assertThat(events("TASK_STARTED")).isEqualTo(1);
        assertThat(taskVersion(second)).isEqualTo(1);
        complete(manager, first, 0).andExpect(status().isConflict());
        complete(manager, first, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
        complete(manager, second, 1).andExpect(status().isOk());
        assertThat(setState(set)).isEqualTo("COMPLETED");
    }

    @Test
    void concurrentLastPredecessorsOpenTaskAfterBothCommit() throws Exception {
        UUID set = set(), first = node(set, "IN_PROGRESS"), second = node(set, "IN_PROGRESS"), last = node(set, "NEW");
        depends(last, first, second);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(
                    () -> complete(manager, first, 0).andReturn().getResponse().getStatus());
            var b = pool.submit(
                    () -> complete(manager, second, 0).andReturn().getResponse().getStatus());
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(taskStatus(last)).isEqualTo("IN_PROGRESS");
        assertThat(taskVersion(last)).isEqualTo(1);
        assertThat(events("TASK_STARTED")).isEqualTo(1);
    }

    @Test
    void reviewSubmissionDoesNotSatisfyDependencyUntilFinalApproval() throws Exception {
        UUID set = set(), first = node(set, "IN_PROGRESS"), second = node(set, "NEW");
        depends(second, first);
        jdbc.update("update tasks set needs_review=true,result_recipient_id=? where id=?", lead, first);
        complete(manager, first, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));
        assertThat(taskStatus(second)).isEqualTo("NEW");
        complete(manager, second, 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.blockingTasks[0].status").value("WAITING_APPROVAL"));
        finished(first, lead);
        assertThat(taskStatus(second)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void twentyTaskChainProgressesToCompletionWithoutManualStatusChanges() throws Exception {
        UUID set = set();
        var tasks = new ArrayList<UUID>();
        for (int index = 0; index < 20; index++) {
            UUID id = node(set, index == 0 ? "IN_PROGRESS" : "NEW");
            tasks.add(id);
            if (index > 0) depends(id, tasks.get(index - 1));
        }
        for (int index = 0; index < tasks.size(); index++) {
            UUID id = tasks.get(index);
            assertThat(taskStatus(id)).isEqualTo("IN_PROGRESS");
            complete(manager, id, taskVersion(id)).andExpect(status().isOk());
            if (index + 1 < tasks.size())
                assertThat(taskStatus(tasks.get(index + 1))).isEqualTo("IN_PROGRESS");
        }
        assertThat(setState(set)).isEqualTo("COMPLETED");
        assertThat(events("TASK_STARTED")).isEqualTo(19);
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where task_set_id=? and completed_at is not null",
                        Integer.class,
                        set))
                .isEqualTo(20);
    }

    @Test
    void notificationFailureRollsBackCompletionOpeningAndHistory() throws Exception {
        UUID set = set(), first = node(set, "IN_PROGRESS"), second = node(set, "NEW");
        depends(second, first);
        jdbc.execute(
                "alter table user_notifications add constraint test_dependency_notification check(event_type<>'TASK_STARTED')");
        try {
            complete(manager, first, 0).andExpect(status().isInternalServerError());
            assertThat(taskStatus(first)).isEqualTo("IN_PROGRESS");
            assertThat(taskVersion(first)).isZero();
            assertThat(taskStatus(second)).isEqualTo("NEW");
            assertThat(taskVersion(second)).isZero();
            assertThat(count("task_history")).isZero();
            assertThat(count("event_outbox")).isZero();
            assertThat(setState(set)).isEqualTo("ACTIVE");
        } finally {
            jdbc.execute("alter table user_notifications drop constraint test_dependency_notification");
        }
    }

    @Test
    void manualPauseSurvivesPredecessorCompletionAndLegacyDependencyPauseCanOpen() throws Exception {
        UUID set = set(), first = node(set, "IN_PROGRESS"), manual = node(set, "PAUSED"), legacy = node(set, "PAUSED");
        depends(manual, first);
        depends(legacy, first);
        jdbc.update("update tasks set manually_paused=true where id=?", manual);
        complete(manager, first, 0).andExpect(status().isOk());
        assertThat(taskStatus(manual)).isEqualTo("PAUSED");
        assertThat(taskVersion(manual)).isZero();
        assertThat(taskStatus(legacy)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void objectPauseDefersOpeningUntilReconciliationAfterResume() {
        UUID set = set(), first = node(set, "IN_PROGRESS"), second = node(set, "NEW");
        depends(second, first);
        state("PAUSED");
        finished(first, manager);
        assertThat(taskStatus(second)).isEqualTo("NEW");
        state("IN_PROGRESS");
        recheck();
        assertThat(taskStatus(second)).isEqualTo("IN_PROGRESS");
    }
}
