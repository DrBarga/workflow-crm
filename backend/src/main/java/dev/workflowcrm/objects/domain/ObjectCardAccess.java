package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectCardAccess {
    private static final Set<String> ROLES =
            Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN");
    private final ObjectAssignmentQuery users;

    ObjectCardAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    ObjectAssignmentQuery.Member require(UUID actor) {
        return users.member(actor)
                .filter(ObjectAssignmentQuery.Member::active)
                .filter(u -> ROLES.contains(u.role()))
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
    }

    boolean full(ObjectAssignmentQuery.Member u, ObjectCardStore.Summary o) {
        return switch (u.role()) {
            case "MANAGER" -> u.id().equals(o.managerId()) && u.belongsTo(o.regionId());
            case "REGIONAL_MANAGER" -> u.belongsTo(o.regionId());
            case "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN" -> true;
            default -> false;
        };
    }

    boolean contractVisible(ObjectAssignmentQuery.Member u) {
        return Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN")
                .contains(u.role());
    }
}
