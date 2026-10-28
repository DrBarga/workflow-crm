package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusRequestLifecycle {
    private final StatusApprovalRequests requests;
    private final ObjectStatusApprovalEvents events;
    private final dev.workflowcrm.approvals.api.EntryPointApprovalRequests pointRequests;
    private final EntryPointApprovalEvents pointEvents;

    ObjectStatusRequestLifecycle(
            StatusApprovalRequests requests,
            ObjectStatusApprovalEvents events,
            dev.workflowcrm.approvals.api.EntryPointApprovalRequests pointRequests,
            EntryPointApprovalEvents pointEvents) {
        this.requests = requests;
        this.events = events;
        this.pointRequests = pointRequests;
        this.pointEvents = pointEvents;
    }

    void complete(UUID actor, RealEstateObject object, ObjectStatusWriteMode mode) {
        if (mode.refusalEntry()) {
            for (var request : pointRequests.cancelPending(object.getId(), actor))
                pointEvents.resolved(request, actor, object.getVersion(), "CANCELLED");
            for (var request : requests.cancelPending(object.getId(), actor, mode.requestId()))
                events.resolved(request, actor, object.getVersion(), "CANCELLED");
        } else {
            requests.invalidate(object.getId(), mode.requestId());
            pointRequests.invalidate(object.getId(), null);
        }
    }
}
