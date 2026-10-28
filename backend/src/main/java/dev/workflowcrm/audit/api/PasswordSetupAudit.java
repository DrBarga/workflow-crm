package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface PasswordSetupAudit {
    void record(UUID userId, String deviceId);
}
