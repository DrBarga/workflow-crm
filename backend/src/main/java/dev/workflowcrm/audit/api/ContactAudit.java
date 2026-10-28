package dev.workflowcrm.audit.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ContactAudit {
    enum Action {
        CREATED,
        UPDATED,
        DELETED
    }

    record Change(String before, String after) {}

    default void record(
            UUID contactId,
            UUID actorId,
            long version,
            Action action,
            List<String> changedFields,
            List<UUID> linkedObjectIds) {
        record(contactId, actorId, version, action, changedFields, linkedObjectIds, Map.of());
    }

    void record(
            UUID contactId,
            UUID actorId,
            long version,
            Action action,
            List<String> changedFields,
            List<UUID> linkedObjectIds,
            Map<String, Change> changedValues);
}
