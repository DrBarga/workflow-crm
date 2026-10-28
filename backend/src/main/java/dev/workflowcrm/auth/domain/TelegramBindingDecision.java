package dev.workflowcrm.auth.domain;

public enum TelegramBindingDecision {
    BOUND,
    REJECTED,
    EXPIRED,
    CHAT_IN_USE,
    ACCOUNT_ALREADY_BOUND
}
