package dev.workflowcrm.tasks.domain;

import java.util.*;

final class TaskDependencyGraph {
    record Change(TaskRuntimeStore.Node task, String status) {}

    record Plan(List<Change> changes, Map<UUID, String> sets) {}

    private TaskDependencyGraph() {}

    static Plan plan(TaskRuntimeStore.Graph graph, boolean runnable) {
        var tasks = new LinkedHashMap<UUID, TaskRuntimeStore.Node>();
        var states = new HashMap<UUID, String>();
        var sets = new HashMap<UUID, List<TaskRuntimeStore.Node>>();
        var successors = new HashMap<UUID, List<UUID>>();
        for (var task : graph.tasks()) {
            tasks.put(task.id(), task);
            states.put(task.id(), task.status());
            if (task.setId() != null)
                sets.computeIfAbsent(task.setId(), key -> new ArrayList<>()).add(task);
        }
        graph.predecessors()
                .forEach((task, predecessors) -> predecessors.forEach(predecessor -> successors
                        .computeIfAbsent(predecessor, key -> new ArrayList<>())
                        .add(task)));
        var cancelled = new ArrayDeque<UUID>();
        var seen = new HashSet<UUID>();
        tasks.values().stream().filter(t -> "CANCELLED".equals(t.status())).forEach(t -> cancelled.add(t.id()));
        while (!cancelled.isEmpty()) {
            UUID id = cancelled.removeFirst();
            if (!seen.add(id)) continue;
            var source = tasks.get(id);
            if (source == null) throw new IllegalStateException("Unknown task dependency");
            if (source.setId() != null && source.activeSet()) {
                for (var sibling : sets.get(source.setId())) cancel(sibling, states, cancelled);
            }
            for (UUID successor : successors.getOrDefault(id, List.of())) {
                var task = tasks.get(successor);
                if (task == null) throw new IllegalStateException("Unknown dependent task");
                if (task.activeSet()) cancel(task, states, cancelled);
            }
        }
        if (runnable) {
            for (var task : tasks.values()) {
                var predecessors = graph.predecessors().getOrDefault(task.id(), List.of());
                boolean waiting = "NEW".equals(states.get(task.id()))
                        || ("PAUSED".equals(states.get(task.id())) && !task.manuallyPaused());
                if (waiting
                        && task.activeSet()
                        && !predecessors.isEmpty()
                        && predecessors.stream().allMatch(id -> "DONE".equals(states.get(id))))
                    states.put(task.id(), "IN_PROGRESS");
            }
        }
        var changes = new ArrayList<Change>();
        tasks.values().forEach(task -> {
            if (!task.status().equals(states.get(task.id()))) changes.add(new Change(task, states.get(task.id())));
        });
        var setChanges = new TreeMap<UUID, String>();
        sets.forEach((id, members) -> {
            if (members.stream().noneMatch(TaskRuntimeStore.Node::activeSet)) return;
            if (members.stream().anyMatch(task -> "CANCELLED".equals(states.get(task.id()))))
                setChanges.put(id, "CANCELLED");
            else if (members.stream().allMatch(task -> "DONE".equals(states.get(task.id()))))
                setChanges.put(id, "COMPLETED");
        });
        return new Plan(List.copyOf(changes), Map.copyOf(setChanges));
    }

    private static void cancel(TaskRuntimeStore.Node task, Map<UUID, String> states, Deque<UUID> queue) {
        if (!Set.of("DONE", "CANCELLED").contains(states.get(task.id()))) {
            states.put(task.id(), "CANCELLED");
            queue.add(task.id());
        }
    }
}
