package dev.workflowcrm.auth.domain;

import java.util.UUID;

public record LoginChallenge(
        String id, UUID userId, String email, String deviceId, String userAgent, String ip, ChallengeStatus status) {
    @Override
    public String toString() {
        return "LoginChallenge[id=" + id + ", status=" + status + ", details redacted]";
    }
}
