package dev.workflowcrm.auth.domain;

public record IssuedRefreshToken(String value, String sessionId) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[redacted]";
    }
}
