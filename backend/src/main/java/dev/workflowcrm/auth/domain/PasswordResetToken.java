package dev.workflowcrm.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(UUID id, UUID userId, Instant expiresAt, Instant consumedAt, Instant invalidatedAt) {
    public boolean usableAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }
}
