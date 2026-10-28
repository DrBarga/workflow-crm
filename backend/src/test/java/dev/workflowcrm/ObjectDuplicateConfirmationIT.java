package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectDuplicateConfirmationIT extends ObjectDuplicateTestSupport {
    @Test
    void sameKeyConfirmsCreationAndReplaysWithoutRepeatingDecisionOrHistory() throws Exception {
        var duplicate = nearby(10);
        String key = key();
        create(manager, key, body()).andExpect(status().isConflict());
        var confirmed = body();
        confirmed.put("confirmDuplicates", true);
        String response = create(manager, key, confirmed)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(create(manager, key, body())
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .isEqualTo(response);
        assertThat(count("objects")).isEqualTo(2);
        assertThat(count("object_duplicate_decisions")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select field_name from object_history where field_name <> 'CREATED'", String.class))
                .isEqualTo("DUPLICATES_CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "select nearest_object_ids[1]::text from object_duplicate_decisions", String.class))
                .isEqualTo(duplicate.toString());
        assertThat(jdbc.queryForObject("select actor_id::text from object_duplicate_decisions", String.class))
                .isEqualTo(manager.toString());
        var changed = body();
        changed.put("name", "Інший об’єкт");
        create(manager, key, changed).andExpect(status().isConflict());
    }

    @Test
    void differentConcurrentKeysDoNotBypassDuplicateWarning() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return create(manager, key(), body()).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return create(colleague, key(), body())
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void confirmationMustBeBooleanAndFalseStillWarns() throws Exception {
        nearby(10);
        for (Object flag : java.util.List.of("true", 1, java.util.Map.of("value", true))) {
            var body = body();
            body.put("confirmDuplicates", flag);
            create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("confirmDuplicates"));
        }
        var body = body();
        body.put("confirmDuplicates", false);
        create(manager, key(), body).andExpect(status().isConflict());
    }
}
