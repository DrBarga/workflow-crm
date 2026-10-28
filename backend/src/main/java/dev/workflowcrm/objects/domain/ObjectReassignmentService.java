package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskReassignment;
import dev.workflowcrm.users.api.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectReassignmentService {
    private final ObjectAssignmentQuery users;
    private final ObjectAssignmentLock members;
    private final ObjectStatusAccess access;
    private final ObjectStatusStore store;
    private final ObjectTaskReassignment tasks;
    private final ObjectReassignmentWriter writer;

    ObjectReassignmentService(
            ObjectAssignmentQuery users,
            ObjectAssignmentLock members,
            ObjectStatusAccess access,
            ObjectStatusStore store,
            ObjectTaskReassignment tasks,
            ObjectReassignmentWriter writer) {
        this.users = users;
        this.members = members;
        this.access = access;
        this.store = store;
        this.tasks = tasks;
        this.writer = writer;
    }

    public void requireActor(UUID actor) {
        requireActor(users.member(actor).orElse(null));
    }

    @Transactional
    public ObjectReassignmentResult reassign(UUID actor, UUID id, UUID manager, long version) {
        var object = store.lock(id);
        var locked = members.members(new HashSet<>(List.of(actor, manager)));
        requireActor(locked.get(actor));
        access.require(actor, object);
        if (version != object.getVersion()) throw new ObjectEditConflictException(object.getVersion());
        var target = locked.get(manager);
        if (target == null || !target.active() || !target.role().equals("MANAGER"))
            throw new UnprocessableInputException("managerId", "Choose an active manager");
        if (!target.belongsTo(object.getRegionId()))
            throw new UnprocessableInputException("managerId", "Manager must belong to the object's region");
        UUID previous = object.getManagerId();
        if (previous.equals(manager))
            return new ObjectReassignmentResult(false, id, previous, manager, object.getVersion(), List.of());
        var transferred = tasks.transfer(id, previous, manager, actor);
        object.reassign(manager);
        object.markEdited();
        store.save(object);
        var result = new ObjectReassignmentResult(true, id, previous, manager, object.getVersion(), transferred);
        writer.save(actor, object, result);
        return result;
    }

    private void requireActor(ObjectAssignmentQuery.Member member) {
        if (member == null
                || !member.active()
                || !Set.of("REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR").contains(member.role()))
            throw new AccessDeniedException("Access denied");
    }
}
