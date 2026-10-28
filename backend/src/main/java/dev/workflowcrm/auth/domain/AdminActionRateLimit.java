package dev.workflowcrm.auth.domain;

import java.util.UUID;

public interface AdminActionRateLimit {
    void reserve(UUID administratorId);
}
