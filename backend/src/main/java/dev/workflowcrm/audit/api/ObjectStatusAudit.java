package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface ObjectStatusAudit {
    void record(UUID objectId, UUID actorId, long version, String eventType, Object detail);
}
