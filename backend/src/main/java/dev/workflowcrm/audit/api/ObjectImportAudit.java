package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface ObjectImportAudit {
    void record(UUID job, UUID actor, String event, Object detail);
}
