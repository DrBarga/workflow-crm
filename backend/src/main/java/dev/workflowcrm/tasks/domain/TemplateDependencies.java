package dev.workflowcrm.tasks.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TemplateDependencies {
    private TemplateDependencies() {}

    static void validate(List<TemplateItem> items) {
        var orders = new HashSet<Integer>();
        var dependencies = new HashMap<UUID, java.util.Set<UUID>>();
        for (var item : items) {
            if (dependencies.put(item.id(), item.predecessors()) != null || !orders.add(item.sortOrder()))
                throw new IllegalArgumentException("Duplicate template item or sort order");
        }
        validate(dependencies);
    }

    static void validate(Map<UUID, java.util.Set<UUID>> dependencies) {
        var counts = new HashMap<UUID, Integer>();
        Map<UUID, List<UUID>> successors = new HashMap<>();
        dependencies.forEach((id, predecessors) -> counts.put(id, predecessors.size()));
        dependencies.forEach((id, predecessors) -> {
            for (UUID predecessor : predecessors) {
                if (!counts.containsKey(predecessor)) throw new IllegalArgumentException("Unknown predecessor");
                successors
                        .computeIfAbsent(predecessor, key -> new ArrayList<>())
                        .add(id);
            }
        });
        var ready = new ArrayDeque<UUID>();
        counts.forEach((id, count) -> {
            if (count == 0) ready.add(id);
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            UUID id = ready.remove();
            visited++;
            for (UUID successor : successors.getOrDefault(id, List.of()))
                if (counts.compute(successor, (key, count) -> count - 1) == 0) ready.add(successor);
        }
        if (visited != dependencies.size()) throw new IllegalArgumentException("Template dependencies contain a cycle");
    }
}
