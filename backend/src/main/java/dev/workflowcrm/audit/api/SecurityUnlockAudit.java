package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface SecurityUnlockAudit {
    UUID request(UUID administratorId, UUID userId, Action action, UUID blockedDeviceId);

    void complete(UUID eventId, Outcome outcome);

    enum Action {
        DEVICE_UNBLOCK,
        LOGIN_UNLOCK
    }

    enum Outcome {
        APPLIED,
        UNCHANGED,
        FAILED
    }
}
