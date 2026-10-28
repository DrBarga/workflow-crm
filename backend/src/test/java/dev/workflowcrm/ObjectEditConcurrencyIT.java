package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectEditConcurrencyIT extends ObjectEditTestSupport {
    @Test
    void simultaneousEditsReturnCurrentVersionAndPreserveWinner() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<org.springframework.mock.web.MockHttpServletResponse> first = () -> {
                barrier.await();
                return patch(manager, object, Map.of("version", 0, "name", "Перше редагування"))
                        .andReturn()
                        .getResponse();
            };
            Callable<org.springframework.mock.web.MockHttpServletResponse> second = () -> {
                barrier.await();
                return patch(lead, object, Map.of("version", 0, "name", "Друге редагування"))
                        .andReturn()
                        .getResponse();
            };
            var f = workers.submit(first);
            var s = workers.submit(second);
            var responses = List.of(f.get(30, TimeUnit.SECONDS), s.get(30, TimeUnit.SECONDS));
            assertThat(responses.stream().map(r -> r.getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
            var conflict = responses.stream()
                    .filter(r -> r.getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(json.readTree(conflict.getContentAsByteArray())
                            .path("currentVersion")
                            .asLong())
                    .isEqualTo(1);
        }
        assertThat(version(object)).isEqualTo(1);
        String name = card(manager, object).path("name").asText();
        assertThat(name).isIn("Перше редагування", "Друге редагування");
        assertThat(jdbc.queryForObject("select new_value from object_history where field_name='name'", String.class))
                .isEqualTo("\"" + name + "\"");
    }

    @Test
    void staleVersionCannotOverwriteOrAppendHistory() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(manager, object, change(object, "name", "Перше редагування")).andExpect(status().isOk());
        var error = response(patch(lead, object, Map.of("version", 0, "name", "Друге редагування"))
                .andExpect(status().isConflict()));
        assertThat(error.path("currentVersion").asLong()).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackAllChanges() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.execute(
                "alter table object_status_audit add constraint test_edit_audit check(event_type<>'FIELDS_CHANGED')");
        try {
            patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isInternalServerError());
            assertThat(version(object)).isZero();
            assertThat(card(manager, object).path("name").asText()).isEqualTo("Об’єкт");
            assertThat(count("object_history")).isZero();
            assertThat(count("event_outbox")).isZero();
        } finally {
            jdbc.execute("alter table object_status_audit drop constraint test_edit_audit");
        }
    }

    @Test
    void sameValueDoesNotBumpVersionOrWriteAudit() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var result = response(
                patch(manager, object, change(object, "name", "Об’єкт")).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isFalse();
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("object_status_audit")).isZero();
    }
}
