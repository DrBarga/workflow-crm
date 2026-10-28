package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusApprovalEvents {
    private final ObjectStatusAudit audit;
    private final EventOutbox events;
    private final NotificationQueue notifications;

    ObjectStatusApprovalEvents(ObjectStatusAudit audit, EventOutbox events, NotificationQueue notifications) {
        this.audit = audit;
        this.events = events;
        this.notifications = notifications;
    }

    void resolved(StatusApprovalRequests.Request request, UUID actor, long version, String decision) {
        record Decision(UUID requestId, UUID actorId, String decision) {}
        var detail = new Decision(request.id(), actor, decision);
        String key = "status-request-decision:" + request.id();
        events.append(key, "OBJECT_STATUS_APPROVAL_RESOLVED", request.objectId(), detail);
        audit.record(request.objectId(), actor, version, "STATUS_REQUEST_" + decision, detail);
        notifications.enqueue(
                request.initiatorId(), key, "OBJECT_STATUS_APPROVAL_" + decision, request.objectId(), null);
    }
}
