package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class IcDecisionConcurrencyIT extends IcDecisionTestSupport {
    @Test
    void simultaneousConfirmationsCreateOneSetAndTwoHistoryEvents() throws Exception {
        template();
        task("IN_PROGRESS");
        var body = decision("APPROVED");
        body.put(
                "confirmationToken",
                response(decide(lead, body)).path("confirmationToken").asText());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> request = () -> {
                gate.await();
                return decide(lead, body).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            gate.countDown();
            assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 422);
        }
        assertThat(decisionHistoryCount()).isEqualTo(2);
        assertThat(count("tasks")).isEqualTo(2);
        assertThat(count("task_history")).isZero();
    }

    @Test
    void changedCurrentTaskRequiresFreshConfirmation() throws Exception {
        template();
        var task = task("IN_PROGRESS");
        var body = decision("APPROVED");
        body.put(
                "confirmationToken",
                response(decide(lead, body)).path("confirmationToken").asText());
        jdbc.update("update tasks set version=version+1 where id=?", task);
        assertThat(decide(lead, body).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(count("object_history")).isZero();
    }
}
