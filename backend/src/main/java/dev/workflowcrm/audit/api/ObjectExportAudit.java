package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface ObjectExportAudit {
    void record(UUID exportId, UUID actor, String event, Object detail);
}
