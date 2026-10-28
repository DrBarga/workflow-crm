package dev.workflowcrm.users.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ObjectAssignmentLock {
    Map<UUID, ObjectAssignmentQuery.Member> members(Set<UUID> ids);
}
