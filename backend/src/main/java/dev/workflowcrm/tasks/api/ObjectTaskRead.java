package dev.workflowcrm.tasks.api;

import dev.workflowcrm.shared.read.ReadPage;
import java.time.*;
import java.util.*;

public interface ObjectTaskRead {
    record Assignee(UUID id, String fullName) {}

    record Predecessor(UUID id, String name, String status) {}

    record Task(
            UUID id,
            String name,
            Assignee assignee,
            LocalDate deadline,
            String status,
            String statusName,
            UUID taskSetId,
            boolean needsReview,
            boolean manuallyPaused,
            long version,
            List<Predecessor> predecessors) {
        public Task {
            predecessors = List.copyOf(predecessors);
        }
    }

    record TaskSet(UUID id, String triggerStatus, String state, Instant createdAt) {}

    record Page(List<Task> items, List<TaskSet> taskSets, int page, int size, long totalElements, boolean hasMore) {
        public Page {
            items = List.copyOf(items);
            taskSets = List.copyOf(taskSets);
        }
    }

    Page list(UUID objectId, UUID taskSetId, ReadPage page);
}
