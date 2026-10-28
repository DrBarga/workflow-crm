package dev.workflowcrm.objects.domain;

import dev.workflowcrm.files.api.ObjectFile;
import dev.workflowcrm.files.api.ObjectFileContext;
import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import dev.workflowcrm.users.api.ObjectAssignmentLock;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ObjectFileContextAdapter implements ObjectFileContext {
    private final ObjectCardAccess access;
    private final ObjectAssignmentLock users;
    private final ObjectCardStore objects;
    private final ObjectStatusStore locks;
    private final ObjectDeletionAvailability deletions;
    private final ObjectHistoryEvents history;

    ObjectFileContextAdapter(
            ObjectCardAccess access,
            ObjectAssignmentLock users,
            ObjectCardStore objects,
            ObjectStatusStore locks,
            ObjectDeletionAvailability deletions,
            ObjectHistoryEvents history) {
        this.access = access;
        this.users = users;
        this.objects = objects;
        this.locks = locks;
        this.deletions = deletions;
        this.history = history;
    }

    public Access read(UUID actor, UUID objectId) {
        return context(access.require(actor), objectId, 0);
    }

    public Access lock(UUID actor, UUID objectId) {
        var user = users.members(Set.of(actor)).get(actor);
        if (user == null || !user.active()) throw new AccessDeniedException("Access denied");
        return context(user, objectId, locks.lock(objectId).getVersion());
    }

    private Access context(ObjectAssignmentQuery.Member user, UUID objectId, long version) {
        if (deletions.pending(objectId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object deletion is in progress");
        var object = objects.summary(objectId);
        boolean responsible = "MANAGER".equals(user.role()) && user.id().equals(object.managerId());
        boolean fullView =
                switch (user.role()) {
                    case "MANAGER" -> responsible && user.belongsTo(object.regionId());
                    case "REGIONAL_MANAGER" -> user.belongsTo(object.regionId());
                    default -> access.full(user, object);
                };
        return new Access(user.role(), fullView, responsible, version);
    }

    public void uploaded(UUID actor, UUID objectId, long version, ObjectFile file) {
        history.record(objectId, actor, version, ObjectHistoryEvents.Type.FILE_UPLOADED, null, file);
    }

    public void deleted(UUID actor, UUID objectId, long version, ObjectFile file) {
        history.record(objectId, actor, version, ObjectHistoryEvents.Type.FILE_DELETED, file, null);
    }
}
