package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectHistoryEventsIT extends ObjectHistoryTestSupport {
    @Autowired
    ObjectHistoryEvents events;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void editAndDirectPointChangesKeepPreviousAndNewValues() throws Exception {
        UUID object = add("До змін", manager, region);
        patch(manager, object, change(object, "name", "Після змін")).andExpect(status().isOk());
        point(manager, object, coordinates()).andExpect(status().isOk());
        assertThat(historyFields(object)).contains("name", "entrance", "googleMapsUrl");
        var first = history(manager, object, Map.of()).path("items").get(0);
        assertThat(first.path("previousValue").asText()).isEqualTo("\"До змін\"");
        assertThat(first.path("newValue").asText()).isEqualTo("\"Після змін\"");
        assertThat(first.path("objectVersion").asLong()).isEqualTo(1);
    }

    @Test
    void pendingApprovalAndDecisionAreRecordedExactlyOnce() throws Exception {
        UUID object = add("Підтвердження", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        assertThat(request(manager, object)).isEqualTo(request);
        assertThat(historyFields(object)).containsExactly("ENTRY_POINT_APPROVAL_REQUESTED");
        decide(lead, object, request, "approve").andExpect(status().isOk());
        decide(lead, object, request, "approve").andExpect(status().isConflict());
        assertThat(historyFields(object))
                .containsOnlyOnce("ENTRY_POINT_APPROVAL_REQUESTED", "ENTRY_POINT_APPROVAL_APPROVED")
                .contains("entrance", "googleMapsUrl");
    }

    @Test
    void staleRequestIsRecordedAsSystemAction() throws Exception {
        UUID object = add("Застарілий запит", manager, region);
        state(object, "IC_PREPARATION");
        request(manager, object);
        patch(manager, object, change(object, "name", "Змінено")).andExpect(status().isOk());
        var rows = history(manager, object, Map.of()).path("items");
        var stale = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .filter(e -> e.path("field").asText().equals("ENTRY_POINT_APPROVAL_STALE"))
                .findFirst()
                .orElseThrow();
        assertThat(stale.path("author").path("type").asText()).isEqualTo("SYSTEM");
        assertThat(stale.path("author").path("id").isNull()).isTrue();
        assertThat(stale.path("author").path("name").asText()).isEqualTo("Система");
    }

    @ParameterizedTest
    @EnumSource(ObjectHistoryEvents.Type.class)
    void moduleEventsPersistTheirPayloadAndParticipateInRollback(ObjectHistoryEvents.Type type) throws Exception {
        UUID object = add("Події модуля", manager, region);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(
                s -> events.record(object, manager, 0, type, Map.of("value", "old"), Map.of("value", "new")));
        tx.executeWithoutResult(s -> {
            events.record(object, null, 0, type, null, Map.of("reason", "automatic"));
            s.setRollbackOnly();
        });
        var rows = history(manager, object, Map.of()).path("items");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("field").asText()).isEqualTo(type.name());
        assertThat(json.readTree(rows.get(0).path("previousValue").asText())
                        .path("value")
                        .asText())
                .isEqualTo("old");
    }

    @Test
    void recorderRequiresTransactionAndRejectsStaleVersion() {
        UUID object = add("Версія", manager, region);
        assertThatThrownBy(
                        () -> events.record(object, manager, 0, ObjectHistoryEvents.Type.FILE_UPLOADED, null, Map.of()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                        .executeWithoutResult(s -> events.record(
                                object, manager, 1, ObjectHistoryEvents.Type.FILE_UPLOADED, null, Map.of())))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(count("object_history")).isZero();
    }

    @Test
    void systemModuleEventSurvivesWithoutUserAuthor() throws Exception {
        UUID object = add("Система", manager, region);
        new TransactionTemplate(transactions)
                .executeWithoutResult(s -> events.record(
                        object,
                        null,
                        0,
                        ObjectHistoryEvents.Type.TASK_DEADLINES_SHIFTED,
                        Map.of("deadline", "2026-10-08"),
                        Map.of("deadline", "2026-10-09", "reason", "holiday")));
        assertThat(history(manager, object, Map.of())
                        .path("items")
                        .get(0)
                        .path("author")
                        .path("name")
                        .asText())
                .isEqualTo("Система");
    }
}
