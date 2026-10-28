package dev.workflowcrm.users.domain;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserRegionPolicy {
    private final Set<RoleCode> requiredRoles;

    public UserRegionPolicy(UserProvisioningProperties properties) {
        requiredRoles = Set.copyOf(properties.regionRequiredRoles());
    }

    public void validate(RoleCode role, java.util.Collection<UUID> regionIds) {
        if (requiredRoles.contains(role) && regionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region is required for this role");
        }
    }
}
