package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.api.*;
import dev.workflowcrm.users.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class JpaObjectAssignmentLock implements ObjectAssignmentLock {
    private final EntityManager entities;
    private final ObjectAssignmentQuery users;

    JpaObjectAssignmentLock(EntityManager entities, ObjectAssignmentQuery users) {
        this.entities = entities;
        this.users = users;
    }

    public Map<UUID, ObjectAssignmentQuery.Member> members(Set<UUID> ids) {
        var result = new HashMap<UUID, ObjectAssignmentQuery.Member>();
        ids.stream().sorted().forEach(id -> {
            var user = entities.find(User.class, id, LockModeType.PESSIMISTIC_READ);
            if (user != null) {
                entities.refresh(user, LockModeType.PESSIMISTIC_READ);
                users.member(id).ifPresent(member -> result.put(id, member));
            }
        });
        return Map.copyOf(result);
    }
}
