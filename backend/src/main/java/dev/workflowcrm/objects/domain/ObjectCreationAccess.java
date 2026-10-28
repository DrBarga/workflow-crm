package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ObjectCreationAccess {
    private final ObjectAssignmentQuery users;

    public ObjectCreationAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    public ObjectAssignmentQuery.Member creator(UUID actorId) {
        var actor = users.member(actorId).orElseThrow(ObjectCreationAccess::denied);
        if (!actor.active()
                || !Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR")
                        .contains(actor.role())) throw denied();
        if (!"DEVELOPMENT_DIRECTOR".equals(actor.role()) && actor.regionIds().isEmpty()) throw denied();
        return actor;
    }

    public UUID manager(ObjectAssignmentQuery.Member actor, UUID requested, UUID regionId) {
        if (!"DEVELOPMENT_DIRECTOR".equals(actor.role()) && !actor.belongsTo(regionId)) throw denied();
        if ("MANAGER".equals(actor.role())) {
            if (requested != null && !requested.equals(actor.id())) throw denied();
            return actor.id();
        }
        if (requested == null)
            throw new dev.workflowcrm.shared.error.UnprocessableInputException(
                    "managerId", "Choose a manager from your region");
        var manager = users.member(requested).orElseThrow(ObjectCreationAccess::denied);
        if (!manager.active() || !"MANAGER".equals(manager.role()) || !manager.belongsTo(regionId)) throw denied();
        return manager.id();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
