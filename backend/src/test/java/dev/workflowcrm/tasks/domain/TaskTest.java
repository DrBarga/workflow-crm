package dev.workflowcrm.tasks.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {
    final UUID id = UUID.randomUUID();
    final Instant created = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void permitsManualTasksWithoutTemplateSource() {
        assertThat(task(TaskStatus.NEW, null, null, null, 0).templateVersionId())
                .isNull();
    }

    @Test
    void completionTimeRequiresCompletedStatusAndCannotPrecedeCreation() {
        assertThat(task(TaskStatus.DONE, created.plusSeconds(1), id, id, 1).completedAt())
                .isAfter(created);
        assertThatThrownBy(() -> task(TaskStatus.IN_PROGRESS, created, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task(TaskStatus.DONE, created.minusSeconds(1), null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresCompleteSourceReferenceAndNonnegativeRecordVersion() {
        assertThatThrownBy(() -> task(TaskStatus.NEW, null, id, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task(TaskStatus.NEW, null, null, id, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task(TaskStatus.NEW, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifiesOnlyDoneAndCancelledAsTerminal() {
        for (var status : TaskStatus.values())
            assertThat(status.terminal()).isEqualTo(status == TaskStatus.DONE || status == TaskStatus.CANCELLED);
    }

    @Test
    void manualPauseIsAllowedOnlyForPausedTasks() {
        assertThat(task(TaskStatus.PAUSED, null, null, null, 0, true).manuallyPaused())
                .isTrue();
        for (var status : TaskStatus.values())
            if (status != TaskStatus.PAUSED)
                assertThatThrownBy(() -> task(status, null, null, null, 0, true))
                        .isInstanceOf(IllegalArgumentException.class);
    }

    private Task task(TaskStatus status, Instant completed, UUID source, UUID templateVersion, long version) {
        return task(status, completed, source, templateVersion, version, false);
    }

    private Task task(
            TaskStatus status,
            Instant completed,
            UUID source,
            UUID templateVersion,
            long version,
            boolean manuallyPaused) {
        return new Task(
                id,
                "Перевірити договір",
                id,
                LocalDate.now(),
                id,
                id,
                TaskAuthorType.USER,
                true,
                null,
                status,
                manuallyPaused,
                created,
                completed,
                source,
                templateVersion,
                id,
                version);
    }
}
