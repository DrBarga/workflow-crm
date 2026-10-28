package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectReassignmentUserConcurrencyIT extends ObjectReassignmentTestSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void waitsForConcurrentDeactivationAndRejectsDisabledTarget() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "NEW");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var deactivation = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("update users set status='DISABLED' where id=?", colleague);
                locked.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Lock timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            var reassignment = workers.submit(() -> reassign(lead, object, Map.of("version", 0, "managerId", colleague))
                    .andReturn()
                    .getResponse());
            try {
                assertThatThrownBy(() -> reassignment.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            deactivation.get(30, TimeUnit.SECONDS);
            assertThat(reassignment.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(422);
        } finally {
            release.countDown();
        }
        assertThat(owner(object)).isEqualTo(manager);
        assertThat(assignee(task)).isEqualTo(manager);
        assertThat(version(object)).isZero();
        assertThat(count("user_notifications")).isZero();
    }
}
