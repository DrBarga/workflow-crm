package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskDependencyCancellationIT extends TaskDependencyTestSupport {
    @Test
    void cancelledPredecessorCancelsWholeUnfinishedSetButPreservesCompletedTasks() {
        UUID set = set(),
                first = node(set, "IN_PROGRESS"),
                second = node(set, "NEW"),
                parallel = node(set, "IN_PROGRESS"),
                done = node(set, "DONE");
        depends(second, first);
        cancelled(first);
        assertThat(taskStatus(first)).isEqualTo("CANCELLED");
        assertThat(taskStatus(second)).isEqualTo("CANCELLED");
        assertThat(taskStatus(parallel)).isEqualTo("CANCELLED");
        assertThat(taskStatus(done)).isEqualTo("DONE");
        assertThat(taskVersion(done)).isZero();
        assertThat(setState(set)).isEqualTo("CANCELLED");
        assertThat(events("TASK_CANCELLED")).isEqualTo(3);
        recheck();
        assertThat(events("TASK_CANCELLED")).isEqualTo(3);
    }

    @Test
    void cancellationFollowsCrossSetDependenciesWithoutTouchingUnrelatedSet() {
        UUID firstSet = set("FOUND"), secondSet = set("IN_PROGRESS"), untouchedSet = set("IC_PREPARATION");
        UUID first = node(firstSet, "IN_PROGRESS"),
                dependent = node(secondSet, "NEW"),
                sibling = node(secondSet, "IN_PROGRESS"),
                untouched = node(untouchedSet, "IN_PROGRESS");
        depends(dependent, first);
        cancelled(first);
        assertThat(setState(firstSet)).isEqualTo("CANCELLED");
        assertThat(setState(secondSet)).isEqualTo("CANCELLED");
        assertThat(taskStatus(dependent)).isEqualTo("CANCELLED");
        assertThat(taskStatus(sibling)).isEqualTo("CANCELLED");
        assertThat(taskStatus(untouched)).isEqualTo("IN_PROGRESS");
        assertThat(setState(untouchedSet)).isEqualTo("ACTIVE");
    }

    @Test
    void manualTaskDependencyAlsoCancelsAndDoesNotReopenOnRecheck() {
        UUID first = node(null, "IN_PROGRESS"), second = node(null, "NEW"), third = node(null, "NEW");
        depends(second, first);
        depends(third, second);
        cancelled(first);
        assertThat(taskStatus(second)).isEqualTo("CANCELLED");
        assertThat(taskStatus(third)).isEqualTo("CANCELLED");
        recheck();
        assertThat(events("TASK_STARTED")).isZero();
    }
}
