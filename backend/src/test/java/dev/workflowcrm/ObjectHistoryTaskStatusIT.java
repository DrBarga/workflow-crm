package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.workflowcrm.objects.domain.ObjectHistoryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectHistoryTaskStatusIT extends ObjectStatusTasksSupport {
    @Autowired
    ObjectHistoryService history;

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL"})
    void refusalExposesTaskIdentityOldAndNewStatusAndInitiatorExactlyOnce(String previous) throws Exception {
        UUID task = task(previous);
        var command = command("REFUSED");
        command.put("refusalReasonId", reason());
        change(lead, command).andExpect(status().isOk());
        change(lead, command).andExpect(status().isConflict());
        var entries = history.get(manager, object, null, 100).items().stream()
                .filter(e -> e.field().equals("TASK_STATUS_CHANGED"))
                .toList();
        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.author().id()).isEqualTo(lead);
        assertThat(entry.occurredAt()).isNotNull();
        assertThat(entry.objectVersion()).isZero();
        var before = json.readTree(entry.previousValue());
        var after = json.readTree(entry.newValue());
        assertThat(before.path("taskId").asText()).isEqualTo(task.toString());
        assertThat(after.path("taskId")).isEqualTo(before.path("taskId"));
        assertThat(before.path("name").asText()).isEqualTo("Статусна задача");
        assertThat(after.path("name")).isEqualTo(before.path("name"));
        assertThat(before.path("status").asText()).isEqualTo(previous);
        assertThat(after.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(before.path("version").asLong()).isZero();
        assertThat(after.path("version").asLong()).isEqualTo(1);
    }

    @Test
    void terminalTasksProduceNoStatusChangeHistory() throws Exception {
        task("DONE");
        task("CANCELLED");
        var command = command("REFUSED");
        command.put("refusalReasonId", reason());
        change(manager, command).andExpect(status().isOk());
        assertThat(history.get(manager, object, null, 100).items())
                .noneMatch(e -> e.field().equals("TASK_STATUS_CHANGED"));
    }

    @Test
    void taskHistoryFailureRollsBackTasksObjectOutboxAndNotifications() throws Exception {
        task("NEW");
        var before = taskSnapshot();
        long originalVersion = version();
        int originalEvents = events("TASK_CANCELLED");
        int originalNotifications = count("user_notifications");
        jdbc.execute(
                "alter table object_history add constraint test_task_status_history check(field_name <> 'TASK_STATUS_CHANGED')");
        try {
            var command = command("REFUSED");
            command.put("refusalReasonId", reason());
            change(manager, command).andExpect(status().isInternalServerError());
            assertThat(taskSnapshot()).isEqualTo(before);
            assertThat(version()).isEqualTo(originalVersion);
            assertThat(state()).isEqualTo("FOUND");
            assertThat(count("task_history")).isZero();
            assertThat(count("object_history")).isZero();
            assertThat(events("TASK_CANCELLED")).isEqualTo(originalEvents);
            assertThat(count("user_notifications")).isEqualTo(originalNotifications);
        } finally {
            jdbc.execute("alter table object_history drop constraint test_task_status_history");
        }
    }
}
