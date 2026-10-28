package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectStatusConcurrencyIT extends ObjectStatusTasksSupport {
    @Test
    void concurrentConfirmedCommandsCreateOnlyOneTaskSet() throws Exception {
        template("IN_PROGRESS", null);
        var body = command("IN_PROGRESS");
        body.put(
                "confirmationToken",
                response(change(manager, body)).path("confirmationToken").asText());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> call = () -> {
                barrier.await();
                return change(manager, body).andReturn().getResponse().getStatus();
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(version()).isEqualTo(1);
        assertThat(taskSnapshot()).hasSize(1);
        assertThat(events("TASK_GENERATION_REQUESTED")).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackCancellationObjectHistoryAndNotifications() throws Exception {
        state("IN_PROGRESS");
        task("IN_PROGRESS");
        var before = taskSnapshot();
        jdbc.execute(
                "alter table object_status_audit add constraint test_audit_failure check(event_type<>'STATUS_CHANGED')");
        try {
            var body = command("REFUSED");
            body.put("refusalReasonId", reason());
            change(manager, body).andExpect(status().isInternalServerError());
            assertThat(taskSnapshot()).isEqualTo(before);
            assertThat(state()).isEqualTo("IN_PROGRESS");
            assertThat(version()).isZero();
            assertThat(count("object_history")).isZero();
            assertThat(count("user_notifications")).isZero();
            assertThat(events("TASK_CANCELLED")).isZero();
        } finally {
            jdbc.execute("alter table object_status_audit drop constraint test_audit_failure");
        }
    }
}
