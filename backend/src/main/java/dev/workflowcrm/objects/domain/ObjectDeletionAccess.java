package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentLock;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectDeletionAccess {
    private final ObjectAssignmentQuery users;
    private final ObjectAssignmentLock locks;

    ObjectDeletionAccess(ObjectAssignmentQuery users, ObjectAssignmentLock locks) {
        this.users = users;
        this.locks = locks;
    }

    void require(UUID actor) {
        check(users.member(actor).orElse(null));
    }

    void lock(UUID actor) {
        check(locks.members(Set.of(actor)).get(actor));
    }

    private void check(ObjectAssignmentQuery.Member user) {
        if (user == null || !user.active() || !"SUPERADMIN".equals(user.role()))
            throw new AccessDeniedException("Administrator access required");
    }
}
