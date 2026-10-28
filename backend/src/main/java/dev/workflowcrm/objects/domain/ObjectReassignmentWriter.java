package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.*;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ObjectReassignmentWriter {
    private final ObjectEditStore history;
    private final ObjectStatusAudit audit;
    private final EventOutbox events;
    private final NotificationQueue notifications;
    private final StatusApprovalRequests statuses;
    private final EntryPointApprovalRequests points;

    ObjectReassignmentWriter(
            ObjectEditStore history,
            ObjectStatusAudit audit,
            EventOutbox events,
            NotificationQueue notifications,
            StatusApprovalRequests statuses,
            EntryPointApprovalRequests points) {
        this.history = history;
        this.audit = audit;
        this.events = events;
        this.notifications = notifications;
        this.statuses = statuses;
        this.points = points;
    }

    void save(UUID actor, RealEstateObject object, ObjectReassignmentResult result) {
        history.history(
                actor,
                object,
                Map.of("managerId", result.previousManagerId().toString()),
                Map.of("managerId", object.getManagerId().toString()));
        record Detail(UUID actorId, ObjectReassignmentResult reassignment) {}
        var detail = new Detail(actor, result);
        audit.record(object.getId(), actor, object.getVersion(), "MANAGER_REASSIGNED", detail);
        String key = "object-reassigned:" + object.getId() + ":" + object.getVersion();
        events.append(key, "OBJECT_MANAGER_REASSIGNED", object.getId(), detail);
        notifications.enqueue(result.previousManagerId(), key, "OBJECT_MANAGER_REASSIGNED", object.getId(), null);
        notifications.enqueue(result.managerId(), key, "OBJECT_MANAGER_REASSIGNED", object.getId(), null);
        statuses.invalidate(object.getId(), null);
        points.invalidate(object.getId(), null);
    }
}
