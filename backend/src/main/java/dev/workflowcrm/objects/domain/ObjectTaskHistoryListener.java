package dev.workflowcrm.objects.domain;

import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import dev.workflowcrm.tasks.api.TaskSetCreated;
import dev.workflowcrm.tasks.api.TaskStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ObjectTaskHistoryListener {
    private final ObjectHistoryEvents history;
    private final ObjectStatusAudit audit;
    private final ObjectStatusStore objects;

    ObjectTaskHistoryListener(ObjectHistoryEvents history, ObjectStatusAudit audit, ObjectStatusStore objects) {
        this.history = history;
        this.audit = audit;
        this.objects = objects;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void created(TaskSetCreated event) {
        var object = objects.lock(event.objectId());
        objects.save(object);
        history.record(event.objectId(), event.actorId(), ObjectHistoryEvents.Type.TASK_SET_CREATED, null, event);
        audit.record(event.objectId(), event.actorId(), object.getVersion(), "TASK_SET_CREATED", event);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(TaskStatusChanged event) {
        history.record(
                event.objectId(),
                event.actorId(),
                ObjectHistoryEvents.Type.TASK_STATUS_CHANGED,
                event.previous(),
                event.current());
    }
}
