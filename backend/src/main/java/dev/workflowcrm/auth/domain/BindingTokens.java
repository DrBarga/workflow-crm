package dev.workflowcrm.auth.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface BindingTokens {
    default String issue(UUID userId, String challengeId) {
        return issue(userId, challengeId, Duration.ofMinutes(15));
    }

    default String issue(UUID userId, String challengeId, Duration ttl) {
        return issue(userId, challengeId, ttl, false);
    }

    String issue(UUID userId, String challengeId, Duration ttl, boolean replaceExisting);

    Optional<BindingRequest> find(String token);

    Optional<BindingRequest> consume(String token);

    void invalidate(UUID userId);
}
