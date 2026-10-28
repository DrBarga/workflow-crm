package dev.workflowcrm.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeStore {
    void reserve(UUID userId);

    LoginChallenge create(VerifiedLogin login, ChallengeStatus status, String emailCode);

    Optional<LoginChallenge> find(String id);

    boolean confirmEmail(String id, String deviceId, String code, ChallengeStatus next);

    boolean transition(String id, ChallengeStatus expected, ChallengeStatus next);

    Optional<LoginChallenge> consume(String id, String deviceId);

    boolean mayResend(String id);
}
