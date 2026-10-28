package dev.workflowcrm.auth.domain;

import java.time.Duration;

public interface ChallengePolicy {
    Duration ttl();

    int maxEmailAttempts();
}
