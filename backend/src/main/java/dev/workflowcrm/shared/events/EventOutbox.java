package dev.workflowcrm.shared.events;

import java.util.UUID;

public interface EventOutbox {
    void append(String key, String type, UUID aggregateId, Object payload);
}
