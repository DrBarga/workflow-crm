package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ContactAccess {
    private static final Set<String> ROLES =
            Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "CONTRACT_MANAGER", "SUPERADMIN");
    private final ObjectAssignmentQuery users;

    ContactAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    ContactScope require(UUID actorId) {
        var actor = users.member(actorId)
                .filter(ObjectAssignmentQuery.Member::active)
                .filter(u -> ROLES.contains(u.role()))
                .orElseThrow(ContactAccess::denied);
        var regions = actor.role().equals("REGIONAL_MANAGER")
                ? actor.regionIds().stream()
                        .flatMap(id -> users.regionTree(id).stream())
                        .distinct()
                        .toList()
                : actor.regionIds();
        return new ContactScope(actorId, actor.role(), regions);
    }

    void requireWriter(ContactScope actor) {
        if (!actor.writer()) throw denied();
    }

    boolean canEdit(ContactScope actor, Contact contact, List<ContactObject> objects) {
        return actor.writer()
                && actor.sees(contact, objects)
                && (!objects.isEmpty() || actor.role().equals("SUPERADMIN"));
    }

    boolean canDelete(ContactScope actor) {
        return actor.role().equals("SUPERADMIN");
    }

    void requireDeletion(ContactScope actor) {
        if (!canDelete(actor)) throw denied();
    }

    void edit(ContactScope actor, Contact contact, List<ContactObject> objects) {
        if (!actor.sees(contact, objects)) throw new ContactNotFoundException();
        if (!canEdit(actor, contact, objects)) throw denied();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
