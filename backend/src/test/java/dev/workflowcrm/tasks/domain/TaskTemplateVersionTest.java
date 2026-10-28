package dev.workflowcrm.tasks.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class TaskTemplateVersionTest {
    final UUID first = UUID.randomUUID(), second = UUID.randomUUID();

    @Test
    void acceptsBranchingDependenciesAndCopiesDefinition() {
        var items = new ArrayList<>(List.of(item(first, 0), item(second, 1, first)));
        var version = version(items);
        items.clear();
        assertThat(version.items()).hasSize(2);
        assertThat(version.items().getLast().predecessors()).containsExactly(first);
        assertThatThrownBy(() -> version.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingPredecessorsAndCycles() {
        assertThatThrownBy(() -> version(List.of(item(first, 0, second))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown predecessor");
        assertThatThrownBy(() -> version(List.of(item(first, 0, second), item(second, 1, first))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsDuplicateItemsOrdersAndSelfDependency() {
        assertThatThrownBy(() -> version(List.of(item(first, 0), item(first, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> version(List.of(item(first, 0), item(second, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(first, 0, first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAssignmentParametersAndDeadlineRange() {
        assertThat(new AssignmentRule(AssignmentRule.Kind.OBJECT_MANAGER, null, null).userId())
                .isNull();
        assertThat(new AssignmentRule(AssignmentRule.Kind.REGIONAL_ROLE, null, first).roleId())
                .isEqualTo(first);
        assertThatThrownBy(() -> new AssignmentRule(AssignmentRule.Kind.EXPLICIT_USER, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssignmentRule(AssignmentRule.Kind.NETWORK_ROLE, first, second))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadlineRule(DeadlineRule.Basis.FROM_START_CALENDAR, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadlineRule(DeadlineRule.Basis.FROM_START_BUSINESS, 3651))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentElementsCannotShareACodeInTheSameVersion() {
        var original = item(first, 0);
        var duplicate = new TemplateItem(
                second,
                "Other task",
                original.deadline(),
                original.assignment(),
                1,
                Set.of(),
                false,
                null,
                null,
                original.code());
        assertThatThrownBy(() -> version(List.of(original, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codes must be unique");
    }

    private TaskTemplateVersion version(List<TemplateItem> items) {
        return new TaskTemplateVersion(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), null, Instant.now(), items);
    }

    private TemplateItem item(UUID id, int order, UUID... predecessors) {
        return new TemplateItem(
                id,
                "Перевірити об’єкт",
                new DeadlineRule(DeadlineRule.Basis.FROM_START_CALENDAR, 3),
                new AssignmentRule(AssignmentRule.Kind.EXPLICIT_USER, first, null),
                order,
                Set.of(predecessors));
    }
}
