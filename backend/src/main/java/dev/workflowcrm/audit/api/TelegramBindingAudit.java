package dev.workflowcrm.audit.api;

import java.util.UUID;

public interface TelegramBindingAudit {
    java.util.Optional<UUID> latestEvent(UUID userId);

    boolean hasPreviousBinding(UUID userId);

    void record(UUID userId, long chatId, Action action);

    enum Action {
        BOUND,
        REBOUND,
        UNBOUND
    }
}
