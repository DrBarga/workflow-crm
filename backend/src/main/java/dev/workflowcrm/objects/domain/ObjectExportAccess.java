package dev.workflowcrm.objects.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectExportAccess {
    private final ObjectListAccess lists;

    ObjectExportAccess(ObjectListAccess lists) {
        this.lists = lists;
    }

    ObjectListScope require(UUID actor) {
        var scope = lists.require(actor);
        if (!Set.of("REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN")
                .contains(scope.role())) throw new AccessDeniedException("Export access denied");
        return scope;
    }

    void unchanged(ObjectListScope current, ObjectListScope requested) {
        if (!current.actorId().equals(requested.actorId())
                || !current.role().equals(requested.role())
                || !new HashSet<>(current.regionIds()).equals(new HashSet<>(requested.regionIds())))
            throw new AccessDeniedException("Export permissions changed; request a new export");
    }
}
