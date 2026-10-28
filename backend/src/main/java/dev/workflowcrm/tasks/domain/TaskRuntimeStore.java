package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.tasks.api.TaskStatusChanged;
import java.util.*;

public interface TaskRuntimeStore {
    record Node(
            UUID id,
            UUID setId,
            String setState,
            String name,
            UUID assigneeId,
            String status,
            long version,
            boolean manuallyPaused,
            boolean needsReview,
            UUID resultRecipientId) {
        public boolean terminal() {
            return Set.of("DONE", "CANCELLED").contains(status);
        }

        public TaskStatusChanged.State state() {
            return new TaskStatusChanged.State(id, name, status, version);
        }

        public boolean activeSet() {
            return setId == null || "ACTIVE".equals(setState);
        }
    }

    record Graph(List<Node> tasks, Map<UUID, List<UUID>> predecessors) {
        public Graph {
            tasks = List.copyOf(tasks);
            predecessors = Map.copyOf(predecessors);
        }

        public Node require(UUID id) {
            return tasks.stream()
                    .filter(task -> task.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Task not found"));
        }

        public List<Node> blockers(UUID taskId) {
            var ids = Set.copyOf(predecessors.getOrDefault(taskId, List.of()));
            return tasks.stream()
                    .filter(task -> ids.contains(task.id()) && !"DONE".equals(task.status()))
                    .toList();
        }
    }

    Graph lock(UUID objectId);

    TaskStatusChanged change(UUID objectId, Node task, String status, UUID actor, String actorType);

    void setState(UUID objectId, UUID setId, String state);
}
