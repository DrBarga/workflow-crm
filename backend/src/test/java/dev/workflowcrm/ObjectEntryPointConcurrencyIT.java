package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectEntryPointConcurrencyIT extends ObjectEditTestSupport {
    @Test
    void simultaneousRequestsDoNotDuplicateRequestsOrNotifications() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<UUID> call = () -> {
                barrier.await();
                return request(manager, object);
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(second.get(30, TimeUnit.SECONDS));
        }
        assertThat(count("entry_point_approval_requests")).isEqualTo(1);
        assertThat(count("user_notifications")).isEqualTo(1);
        assertThat(version(object)).isZero();
    }

    @Test
    void simultaneousApprovalsApplyExactlyOnce() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> call = () -> {
                barrier.await();
                return decide(lead, object, request, "approve")
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(version(object)).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(4);
        assertThat(requestStatus(request)).isEqualTo("APPROVED");
    }

    @Test
    void latePauseStillRequiresApprovalAndRegionIsolationRemains() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "PAUSED");
        jdbc.update(
                "update objects set status_before_pause_id=(select id from object_statuses where code='IC_PREPARATION') where id=?",
                object);
        request(manager, object);
        assertThat(version(object)).isZero();
        UUID foreign = add("Чужий об’єкт", outsider, otherRegion);
        point(manager, foreign, coordinates()).andExpect(status().isForbidden());
        point(lead, foreign, coordinates()).andExpect(status().isForbidden());
    }
}
