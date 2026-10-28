package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectRefusalConcurrencyIT extends ObjectStatusTasksSupport {
    @Test
    void refusalAndApprovalSerializeWithOneSuccessfulMutation() throws Exception {
        UUID request = UUID.fromString(response(change(manager, command("IC_PREPARATION")))
                .path("approvalRequestId")
                .asText());
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var refusal = workers.submit(() -> {
                barrier.await();
                return change(manager, body).andReturn().getResponse().getStatus();
            });
            var approval = workers.submit(() -> {
                barrier.await();
                return approval(lead, request, "approve", Map.of())
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            assertThat(List.of(refusal.get(30, TimeUnit.SECONDS), approval.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        String decision =
                jdbc.queryForObject("select status from status_approval_requests where id=?", String.class, request);
        assertThat(decision).isIn("CANCELLED", "APPROVED");
        assertThat(state()).isEqualTo("CANCELLED".equals(decision) ? "REFUSED" : "IC_PREPARATION");
        assertThat(version()).isEqualTo(1);
        assertThat(events("OBJECT_STATUS_CHANGED")).isEqualTo(1);
        assertThat(events("OBJECT_STATUS_APPROVAL_RESOLVED")).isEqualTo(1);
    }
}
