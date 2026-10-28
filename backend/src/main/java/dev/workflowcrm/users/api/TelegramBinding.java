package dev.workflowcrm.users.api;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface TelegramBinding {
    OptionalLong chatId(UUID userId);

    Optional<String> email(UUID userId);

    boolean belongsToAnotherUser(UUID userId, long chatId);

    TelegramBindingState state(UUID userId);

    void bind(UUID userId, long chatId);

    void replace(UUID userId, long chatId);

    void unbind(UUID userId);
}
