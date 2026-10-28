package dev.workflowcrm.tasks.domain;

import java.util.Objects;
import java.util.UUID;

public record AssignmentRule(Kind kind, UUID userId, UUID roleId) {
    public enum Kind {
        EXPLICIT_USER,
        OBJECT_MANAGER,
        REGIONAL_ROLE,
        NETWORK_ROLE
    }

    public AssignmentRule {
        Objects.requireNonNull(kind);
        boolean valid =
                switch (kind) {
                    case EXPLICIT_USER -> userId != null && roleId == null;
                    case OBJECT_MANAGER -> userId == null && roleId == null;
                    case REGIONAL_ROLE, NETWORK_ROLE -> userId == null && roleId != null;
                };
        if (!valid) throw new IllegalArgumentException("Assignment parameters do not match the rule");
    }
}
