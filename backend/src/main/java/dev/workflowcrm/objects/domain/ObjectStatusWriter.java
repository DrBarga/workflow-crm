package dev.workflowcrm.objects.domain;

import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusWriter {
    private final ObjectStatusStore store;
    private final ObjectStatusAudit audit;
    private final EventOutbox events;
    private final NotificationQueue notifications;
    private final ObjectStatusRequestLifecycle requests;

    ObjectStatusWriter(
            ObjectStatusStore store,
            ObjectStatusAudit audit,
            EventOutbox events,
            NotificationQueue notifications,
            ObjectStatusRequestLifecycle requests) {
        this.store = store;
        this.audit = audit;
        this.events = events;
        this.notifications = notifications;
        this.requests = requests;
    }

    void save(
            UUID actor,
            RealEstateObject object,
            Map<String, String> before,
            boolean missingTemplate,
            ObjectStatusWriteMode mode) {
        store.save(object);
        var after = ObjectStatusSnapshot.of(object);
        store.history(actor, object, "STATUS", before, after);
        record(actor, object, before, missingTemplate, mode);
    }

    void record(
            UUID actor,
            RealEstateObject object,
            Map<String, String> before,
            boolean missingTemplate,
            ObjectStatusWriteMode mode) {
        var after = ObjectStatusSnapshot.of(object);
        record Detail(UUID actorId, long version, Map<String, String> before, Map<String, String> after) {}
        var detail = new Detail(actor, object.getVersion(), before, after);
        audit.record(
                object.getId(),
                actor,
                object.getVersion(),
                mode.statusChanged() ? "STATUS_CHANGED" : "MILESTONES_CHANGED",
                detail);
        String key = object.getId() + ":" + object.getVersion();
        events.append(
                "object-status:" + key,
                mode.statusChanged() ? "OBJECT_STATUS_CHANGED" : "OBJECT_MILESTONES_CHANGED",
                object.getId(),
                detail);
        requests.complete(actor, object, mode);
        if (missingTemplate) {
            store.history(actor, object, "TASK_TEMPLATE_MISSING", after, after);
            audit.record(object.getId(), actor, object.getVersion(), "TASK_TEMPLATE_MISSING", detail);
            events.append("template-missing:" + key, "OBJECT_TASK_TEMPLATE_MISSING", object.getId(), detail);
            notifications.administrators("template-missing:" + key, "OBJECT_TASK_TEMPLATE_MISSING", object.getId());
        }
    }
}
