package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests;
import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.shared.events.EventOutbox;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectEditWriter {
    private final ObjectEditStore store;
    private final ObjectStatusStore status;
    private final ObjectStatusAudit audit;
    private final EventOutbox events;
    private final StatusApprovalRequests statusRequests;
    private final EntryPointApprovalRequests pointRequests;

    ObjectEditWriter(
            ObjectEditStore store,
            ObjectStatusStore status,
            ObjectStatusAudit audit,
            EventOutbox events,
            StatusApprovalRequests statusRequests,
            EntryPointApprovalRequests pointRequests) {
        this.store = store;
        this.status = status;
        this.audit = audit;
        this.events = events;
        this.statusRequests = statusRequests;
        this.pointRequests = pointRequests;
    }

    boolean save(UUID actor, RealEstateObject object, Map<String, String> before, String type, UUID request) {
        var after = store.snapshot(object);
        if (before.equals(after)) return false;
        object.markEdited();
        status.save(object);
        record(actor, object, before, type, request);
        return true;
    }

    void record(UUID actor, RealEstateObject object, Map<String, String> before, String type, UUID request) {
        var after = store.snapshot(object);
        store.history(actor, object, before, after);
        record Detail(UUID actorId, long version, Map<String, String> before, Map<String, String> after) {}
        var detail = new Detail(actor, object.getVersion(), before, after);
        audit.record(object.getId(), actor, object.getVersion(), type, detail);
        events.append(
                type.toLowerCase(java.util.Locale.ROOT) + ":" + object.getId() + ":" + object.getVersion(),
                "OBJECT_" + type,
                object.getId(),
                detail);
        statusRequests.invalidate(object.getId(), null);
        pointRequests.invalidate(object.getId(), request);
    }
}
