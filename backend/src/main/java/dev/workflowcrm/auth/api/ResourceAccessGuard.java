package dev.workflowcrm.auth.api;

import java.util.Set;

public interface ResourceAccessGuard {
    void requireAccess(ResourceScope resource, Set<String> allowedRoles, AccessScope scope);
}
