package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectCleanupConcurrencyIT extends ObjectStatusTasksSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void cleanupWaitsForParentTransactionWithoutBlockingItsHistoryWrite() throws Exception {
        var parentLocked = new CountDownLatch(1);
        var writeHistory = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var worker = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.execute("lock table objects in row exclusive mode");
                parentLocked.countDown();
                try {
                    if (!writeHistory.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("History write was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                jdbc.execute("lock table object_history in row exclusive mode");
            }));
            try {
                assertThat(parentLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var cleanup = executor.submit(() -> ObjectTestCleanup.erase(jdbc, () -> {}));
                await().atMost(Duration.ofSeconds(5))
                        .until(() -> jdbc.queryForObject(
                                "select exists(select 1 from pg_locks where relation='objects'::regclass and not granted)",
                                Boolean.class));
                writeHistory.countDown();
                worker.get(10, TimeUnit.SECONDS);
                cleanup.get(10, TimeUnit.SECONDS);
                assertThat(state()).isEqualTo("FOUND");
            } finally {
                writeHistory.countDown();
            }
        }
    }
}
