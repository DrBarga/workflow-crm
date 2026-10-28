package dev.workflowcrm.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenStore {
    void invalidateForUser(UUID userId, Instant invalidatedAt);

    void save(UUID id, UUID userId, String tokenHash, Instant expiresAt);

    Optional<PasswordResetToken> lock(String tokenHash);

    void consume(UUID id, Instant consumedAt);
}
