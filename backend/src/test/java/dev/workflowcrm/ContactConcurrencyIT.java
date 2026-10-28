package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ContactConcurrencyIT extends ContactApiTestSupport {
    @Test
    void competingEditsCommitExactlyOneVersionAndOneHistoryEvent() throws Exception {
        var id = contactId(createdContact());
        link(objectId(manager), id);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return patchContact(manager, id, Map.of("version", 0, "name", "First"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return patchContact(manager, id, Map.of("version", 0, "name", "Second"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select version from contacts where id=?", Long.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from contact_history where contact_id=?", Integer.class, id))
                .isEqualTo(2);
    }
}
