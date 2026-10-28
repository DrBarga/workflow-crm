package dev.workflowcrm.auth.domain;

import java.util.UUID;

public record LoginResult(UUID id, String role, UUID regionId, String name, String token, String refreshToken) {

    @Override
    public String toString() {
        return "LoginResult[id=" + id + ", token redacted]";
    }
}
