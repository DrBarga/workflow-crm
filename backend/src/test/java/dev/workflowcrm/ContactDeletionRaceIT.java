package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import dev.workflowcrm.contacts.domain.ContactDeletionService;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ContactDeletionRaceIT extends ContactApiTestSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    ContactDeletionService deletion;

    @Test
    void attachmentWaitsForDeletionCommitAndRejectsTheDeletedContact() throws Exception {
        var id = contactId(createdContact());
        var deleted = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var body = body();
        body.put("contactId", id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var remove = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                deletion.delete(admin, id, 0, null);
                deleted.countDown();
                try {
                    if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit not released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();
                var attach = executor.submit(() ->
                        create(manager, key(), body).andReturn().getResponse().getStatus());
                await().atMost(Duration.ofSeconds(5))
                        .until(() -> jdbc.queryForObject(
                                """
                        select exists(select 1 from pg_stat_activity
                        where wait_event_type='Lock' and query like 'select id from contacts%')
                        """,
                                Boolean.class));
                commit.countDown();
                remove.get(10, TimeUnit.SECONDS);
                assertThat(attach.get(10, TimeUnit.SECONDS)).isEqualTo(422);
            } finally {
                commit.countDown();
            }
        }
        assertThat(count("objects")).isZero();
        assertThat(count("object_contacts")).isZero();
        assertThat(count("contact_history")).isEqualTo(2);
    }
}
