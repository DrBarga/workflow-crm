package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ObjectHistoryCurrentVersionIT extends ObjectHistoryTestSupport {
    @Autowired
    ObjectHistoryEvents events;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void currentVersionRecorderRequiresTransactionAndRollsBackWithTheOperation() throws Exception {
        UUID object = add("Пов'язана операція", manager, region);
        jdbc.update("update objects set version=7 where id=?", object);
        var type = ObjectHistoryEvents.Type.TASK_STATUS_CHANGED;
        assertThatThrownBy(() -> events.record(object, manager, type, null, Map.of()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> events.record(object, manager, type, "NEW", "IN_PROGRESS"));
        tx.executeWithoutResult(s -> {
            events.record(object, null, type, "IN_PROGRESS", "PAUSED");
            s.setRollbackOnly();
        });
        var rows = history(manager, object, Map.of()).path("items");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("objectVersion").asLong()).isEqualTo(7);
        assertThat(json.readTree(rows.get(0).path("newValue").asText()).asText())
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isEqualTo(7);
    }

    @Test
    void automaticRelatedOperationUsesSystemAuthor() throws Exception {
        UUID object = add("Automatic history", manager, region);
        new TransactionTemplate(transactions)
                .executeWithoutResult(s -> events.record(
                        object,
                        null,
                        ObjectHistoryEvents.Type.TASK_STATUS_CHANGED,
                        Map.of("status", "NEW"),
                        Map.of("status", "IN_PROGRESS")));
        var row = history(manager, object, Map.of()).path("items").get(0);
        assertThat(row.path("author").path("id").isNull()).isTrue();
        assertThat(row.path("author").path("type").asText()).isEqualTo("SYSTEM");
        assertThat(row.path("objectVersion").asLong()).isZero();
    }
}
