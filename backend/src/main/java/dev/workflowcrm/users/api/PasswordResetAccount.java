package dev.workflowcrm.users.api;

import java.util.UUID;

public record PasswordResetAccount(UUID id, String email, String fullName) {}
