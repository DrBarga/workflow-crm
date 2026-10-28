package dev.workflowcrm.auth.domain;

import java.time.Instant;

public record DeviceSession(String id, String userAgent, String ipAddress, Instant lastLoginAt) {}
