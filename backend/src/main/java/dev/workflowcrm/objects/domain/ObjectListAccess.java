package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectListAccess {
    private final ObjectAssignmentQuery users;

    ObjectListAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    ObjectListScope require(UUID actor) {
        var member = users.member(actor)
                .filter(ObjectAssignmentQuery.Member::active)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        List<UUID> regions =
                switch (member.role()) {
                    case "MANAGER", "REGIONAL_MANAGER" -> member.regionIds();
                    case "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN", "CONTRACT_MANAGER" -> List.of();
                    default -> throw new AccessDeniedException("Access denied");
                };
        return new ObjectListScope(actor, member.role(), regions);
    }
}
