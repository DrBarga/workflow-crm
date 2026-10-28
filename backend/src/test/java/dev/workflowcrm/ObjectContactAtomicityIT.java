package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectContactAtomicityIT extends ObjectApiTestSupport {
    @Test
    void auditFailureRollsBackContactAndCanRetryWithSameKey() throws Exception {
        var body = withContact();
        String key = key();
        jdbc.execute(
                "alter table object_history add constraint test_contact_audit_failure check (field_name <> 'CREATED')");
        try {
            create(manager, key, body).andExpect(status().isInternalServerError());
            assertNothingSaved();
        } finally {
            jdbc.execute("alter table object_history drop constraint test_contact_audit_failure");
        }
        create(manager, key, body).andExpect(status().isCreated());
        assertThat(count("contacts")).isEqualTo(1);
    }

    @Test
    void linkFailureRollsBackBothEntitiesAndRequest() throws Exception {
        jdbc.execute("alter table object_contacts add constraint test_contact_link_failure check (version < 0)");
        try {
            create(manager, key(), withContact()).andExpect(status().isInternalServerError());
            assertNothingSaved();
        } finally {
            jdbc.execute("alter table object_contacts drop constraint test_contact_link_failure");
        }
    }

    @Test
    void parallelRetriesCreateOneContactAndOneLink() throws Exception {
        String key = key();
        var body = withContact();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return create(manager, key, body)
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                    }))
                    .toList();
            start.countDown();
            String first = futures.getFirst().get(30, TimeUnit.SECONDS);
            for (var future : futures)
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(first);
        }
        for (String table : java.util.List.of(
                "objects", "contacts", "object_contacts", "object_creation_requests", "object_history"))
            assertThat(count(table)).isEqualTo(1);
    }

    private Map<String, Object> withContact() {
        var body = body();
        body.put("contact", Map.of("name", "Owner", "role", "Owner", "email", "owner@example.com"));
        return body;
    }

    private void assertNothingSaved() {
        for (String table : java.util.List.of(
                "objects", "contacts", "object_contacts", "object_creation_requests", "object_history"))
            assertThat(count(table)).isZero();
    }
}
