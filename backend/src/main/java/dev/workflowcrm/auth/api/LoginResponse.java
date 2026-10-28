package dev.workflowcrm.auth.api;

import java.util.UUID;

public record LoginResponse(UUID id, String role, UUID region, String name) {}
