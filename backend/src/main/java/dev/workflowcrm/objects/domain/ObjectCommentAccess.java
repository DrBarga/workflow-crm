package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentLock;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectCommentAccess {
    private static final Set<String> WRITERS =
            Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER");
    private final ObjectCardAccess cards;
    private final ObjectAssignmentLock users;

    ObjectCommentAccess(ObjectCardAccess cards, ObjectAssignmentLock users) {
        this.cards = cards;
        this.users = users;
    }

    ObjectAssignmentQuery.Member reader(UUID actor) {
        return cards.require(actor);
    }

    ObjectAssignmentQuery.Member writer(UUID actor) {
        var user = cards.require(actor);
        requireWriter(user);
        return user;
    }

    ObjectAssignmentQuery.Member lockWriter(UUID actor) {
        var user = users.members(Set.of(actor)).get(actor);
        requireWriter(user);
        return user;
    }

    void requireObject(ObjectAssignmentQuery.Member user, ObjectCardStore.Summary object) {
        if (!"CONTRACT_MANAGER".equals(user.role()) && !cards.full(user, object))
            throw new AccessDeniedException("Full object access required");
    }

    void requireAuthor(UUID actor, ObjectComment comment) {
        if (!actor.equals(comment.author().id()))
            throw new AccessDeniedException("Only the author can change a comment");
    }

    private void requireWriter(ObjectAssignmentQuery.Member user) {
        if (user == null || !user.active() || !WRITERS.contains(user.role()))
            throw new AccessDeniedException("Comment editing is not allowed");
    }
}
