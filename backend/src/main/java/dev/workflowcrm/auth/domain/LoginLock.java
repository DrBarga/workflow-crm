package dev.workflowcrm.auth.domain;

import java.time.Instant;

public record LoginLock(
        boolean blocked, int failedAttempts, int maxAttempts, long lockDurationSeconds, Instant blockedUntil) {}
