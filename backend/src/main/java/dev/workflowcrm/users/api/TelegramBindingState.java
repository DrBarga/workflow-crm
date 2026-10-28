package dev.workflowcrm.users.api;

import java.util.UUID;

public record TelegramBindingState(boolean linked, UUID revision) {}
