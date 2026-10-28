package dev.workflowcrm.auth.api;

import dev.workflowcrm.auth.domain.DeviceSession;
import java.time.Instant;

record DeviceSessionResponse(String id, String userAgent, String ipAddress, Instant lastLoginAt, boolean current) {

    static DeviceSessionResponse from(DeviceSession session, String currentSessionId) {
        return new DeviceSessionResponse(
                session.id(),
                session.userAgent(),
                session.ipAddress(),
                session.lastLoginAt(),
                session.id().equals(currentSessionId));
    }
}
