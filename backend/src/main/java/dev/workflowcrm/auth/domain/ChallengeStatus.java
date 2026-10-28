package dev.workflowcrm.auth.domain;

public enum ChallengeStatus {
    PENDING_TELEGRAM,
    PENDING_EMAIL,
    BINDING_REQUIRED,
    APPROVED,
    REJECTED,
    CONSUMED,
    EXPIRED
}
