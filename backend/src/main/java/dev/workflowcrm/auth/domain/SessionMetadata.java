package dev.workflowcrm.auth.domain;

public record SessionMetadata(String deviceId, String userAgent, String ip) {
    public static final SessionMetadata EMPTY = new SessionMetadata("", "", "");

    @Override
    public String toString() {
        return "SessionMetadata[redacted]";
    }
}
