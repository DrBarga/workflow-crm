package dev.workflowcrm.auth.domain;

import java.util.UUID;

public record BindingRequest(UUID userId, String challengeId, boolean replaceExisting) {
    public BindingRequest(UUID userId, String challengeId) {
        this(userId, challengeId, false);
    }
}
