package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.TelegramBindingState;
import java.util.UUID;

public record CurrentProfile(
        UUID id, String email, String fullName, String role, UUID regionId, TelegramBindingState telegram) {}
