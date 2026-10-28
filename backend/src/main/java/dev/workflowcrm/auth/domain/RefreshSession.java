package dev.workflowcrm.auth.domain;

import java.util.UUID;

public record RefreshSession(String id, UUID userId) {}
