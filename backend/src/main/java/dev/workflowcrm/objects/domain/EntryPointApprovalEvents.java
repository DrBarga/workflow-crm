package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EntryPointApprovalEvents {
    private final ObjectStatusAudit audit;
    private final NotificationQueue notifications;
    private final EventOutbox events;

    EntryPointApprovalEvents(ObjectStatusAudit audit, NotificationQueue notifications, EventOutbox events) {
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
    }

    void created(EntryPointApprovalRequests.Request request, EntryPointApprovalRoute.Route route) {
        String key = "entry-point-request:" + request.id();
        audit.record(
                request.objectId(),
                request.initiatorId(),
                request.objectVersion(),
                "ENTRY_POINT_APPROVAL_REQUESTED",
                request);
        events.append(key, "OBJECT_ENTRY_POINT_APPROVAL_REQUESTED", request.objectId(), request);
        for (UUID recipient : route.recipients())
            notifications.enqueue(recipient, key, "OBJECT_ENTRY_POINT_APPROVAL_REQUESTED", request.objectId(), null);
    }

    void resolved(EntryPointApprovalRequests.Request request, UUID actor, long version, String decision) {
        record Decision(UUID requestId, UUID actorId, String decision) {}
        var detail = new Decision(request.id(), actor, decision);
        String key = "entry-point-request-decision:" + request.id();
        audit.record(request.objectId(), actor, version, "ENTRY_POINT_REQUEST_" + decision, detail);
        events.append(key, "OBJECT_ENTRY_POINT_APPROVAL_RESOLVED", request.objectId(), detail);
        notifications.enqueue(
                request.initiatorId(), key, "OBJECT_ENTRY_POINT_APPROVAL_" + decision, request.objectId(), null);
    }
}
