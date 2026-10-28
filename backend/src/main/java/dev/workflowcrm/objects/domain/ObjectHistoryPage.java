package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ObjectHistoryPage(List<Entry> items, String nextCursor, boolean hasMore) {
    public record Author(UUID id, ObjectHistory.ActorType type, String name) {}

    public record Entry(
            UUID id,
            long sequence,
            Instant occurredAt,
            Author author,
            String field,
            String previousValue,
            String newValue,
            long objectVersion) {}
}
