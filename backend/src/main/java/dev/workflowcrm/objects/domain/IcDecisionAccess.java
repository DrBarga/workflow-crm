package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class IcDecisionAccess {
    private final ObjectAssignmentQuery users;

    IcDecisionAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    void require(UUID actorId, RealEstateObject object) {
        var actor = users.member(actorId).orElseThrow(IcDecisionAccess::denied);
        if (!actor.active()) throw denied();
        if ("DEVELOPMENT_DIRECTOR".equals(actor.role())) return;
        if ("REGIONAL_MANAGER".equals(actor.role()) && actor.belongsTo(object.getRegionId())) return;
        throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
