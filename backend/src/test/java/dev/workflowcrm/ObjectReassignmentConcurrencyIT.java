package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ObjectReassignmentConcurrencyIT extends ObjectReassignmentTestSupport {
    @Test
    void competingReassignmentsKeepOwnerAndTasksConsistent() throws Exception {
        UUID next = UUID.randomUUID();
        user(next, "MANAGER", region);
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "IN_PROGRESS");
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = workers.submit(() -> {
                barrier.await();
                return reassign(lead, object, Map.of("version", 0, "managerId", colleague))
                        .andReturn()
                        .getResponse();
            });
            var second = workers.submit(() -> {
                barrier.await();
                return reassign(director, object, Map.of("version", 0, "managerId", next))
                        .andReturn()
                        .getResponse();
            });
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
            var winner = results.stream()
                    .filter(r -> r.getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            UUID newOwner = UUID.fromString(json.readTree(winner.getContentAsByteArray())
                    .path("managerId")
                    .asText());
            assertThat(owner(object)).isEqualTo(newOwner);
            assertThat(assignee(task)).isEqualTo(newOwner);
            var conflict = results.stream()
                    .filter(r -> r.getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(json.readTree(conflict.getContentAsByteArray())
                            .path("currentVersion")
                            .asLong())
                    .isEqualTo(1);
        }
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(count("task_assignment_history")).isEqualTo(1);
        assertThat(count("user_notifications")).isEqualTo(2);
    }

    @Test
    void simultaneousPatchAndReassignmentCannotLoseChanges() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = workers.submit(() -> {
                barrier.await();
                return patch(manager, object, Map.of("version", 0, "name", "Нова назва"))
                        .andReturn()
                        .getResponse();
            });
            var second = workers.submit(() -> {
                barrier.await();
                return reassign(lead, object, Map.of("version", 0, "managerId", colleague))
                        .andReturn()
                        .getResponse();
            });
            var edit = first.get(30, TimeUnit.SECONDS);
            var reassignment = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(edit.getStatus(), reassignment.getStatus())).contains(200);
            assertThat(version(object)).isEqualTo(1);
            if (reassignment.getStatus() == 200) {
                assertThat(edit.getStatus()).isIn(403, 409);
                assertThat(owner(object)).isEqualTo(colleague);
                assertThat(card(colleague, object).path("name").asText()).isEqualTo("Об’єкт");
            } else {
                assertThat(reassignment.getStatus()).isEqualTo(409);
                assertThat(owner(object)).isEqualTo(manager);
                assertThat(card(manager, object).path("name").asText()).isEqualTo("Нова назва");
            }
        }
    }
}
