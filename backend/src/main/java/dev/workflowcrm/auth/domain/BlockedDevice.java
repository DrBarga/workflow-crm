package dev.workflowcrm.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record BlockedDevice(UUID id, String deviceId, String userAgent, String ipAddress, Instant blockedAt) {
    @Override
    public String toString() {
        return "BlockedDevice[id=" + id + ", device metadata redacted]";
    }
}
