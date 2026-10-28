package dev.workflowcrm.auth.domain;

import java.time.Instant;

public record TokenPair(String accessToken, String refreshToken, Instant refreshExpiresAt) {
    @Override
    public String toString() {
        return "TokenPair[redacted]";
    }
}
