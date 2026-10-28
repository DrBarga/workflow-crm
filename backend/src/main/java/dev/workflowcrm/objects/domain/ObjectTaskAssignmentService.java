package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.*;
import dev.workflowcrm.users.api.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectTaskAssignmentService {
    private final ObjectStatusStore objects;
    private final ObjectStatusAccess access;
    private final ObjectAssignmentQuery users;
    private final ObjectAssignmentLock members;
    private final TaskAssigneeChange tasks;

    ObjectTaskAssignmentService(
            ObjectStatusStore objects,
            ObjectStatusAccess access,
            ObjectAssignmentQuery users,
            ObjectAssignmentLock members,
            TaskAssigneeChange tasks) {
        this.objects = objects;
        this.access = access;
        this.users = users;
        this.members = members;
        this.tasks = tasks;
    }

    @Transactional
    public List<TaskAssignment.Candidate> options(UUID actor, UUID objectId, UUID taskId) {
        var object = objects.lock(objectId);
        requireActor(actor);
        access.require(actor, object);
        return tasks.options(scope(object), taskId);
    }

    @Transactional
    public TaskAssigneeChange.Result change(UUID actor, UUID objectId, UUID taskId, UUID assigneeId, long version) {
        var object = objects.lock(objectId);
        members.members(new HashSet<>(List.of(actor, assigneeId)));
        requireActor(actor);
        access.require(actor, object);
        return tasks.change(scope(object), taskId, assigneeId, version, actor);
    }

    private void requireActor(UUID actor) {
        var member = users.member(actor).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!member.active()
                || !Set.of("REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR").contains(member.role()))
            throw new AccessDeniedException("Access denied");
    }

    private static TaskAssigneeChange.Scope scope(RealEstateObject object) {
        return new TaskAssigneeChange.Scope(object.getId(), object.getManagerId(), object.getRegionId());
    }
}
