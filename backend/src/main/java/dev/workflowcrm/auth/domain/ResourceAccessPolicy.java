package dev.workflowcrm.auth.domain;

import dev.workflowcrm.auth.api.AccessScope;
import dev.workflowcrm.auth.api.ResourceScope;
import dev.workflowcrm.users.api.AuthenticationUser;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessPolicy {
    public boolean allows(
            AuthenticationUser user, ResourceScope resource, Set<String> allowedRoles, AccessScope scope) {
        if (user == null
                || !user.active()
                || resource == null
                || scope == null
                || allowedRoles == null
                || !allowedRoles.contains(user.role())) {
            return false;
        }
        return switch (scope) {
            case OWNER -> user.id().equals(resource.ownerId());
            case REGION -> user.belongsTo(resource.regionId());
            case NETWORK -> true;
        };
    }
}
