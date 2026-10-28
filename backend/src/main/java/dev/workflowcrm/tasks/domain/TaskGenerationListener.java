package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.tasks.api.TaskGenerationRequested;
import dev.workflowcrm.tasks.api.TaskSetCreated;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TaskGenerationListener {
    private final TaskTransitionStore store;
    private final EventOutbox events;
    private final ApplicationEventPublisher publisher;

    TaskGenerationListener(TaskTransitionStore store, EventOutbox events, ApplicationEventPublisher publisher) {
        this.store = store;
        this.events = events;
        this.publisher = publisher;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void generate(TaskGenerationRequested event) {
        UUID set = store.generate(event.context(), event.plan(), event.actorId());
        publisher.publishEvent(new TaskSetCreated(
                event.context().objectId(),
                event.actorId(),
                set,
                event.plan().templateId(),
                event.plan().tasks().size()));
        record Generation(UUID objectId, UUID statusId, UUID templateId, boolean handledInline, int taskCount) {}
        var context = event.context();
        var detail = new Generation(
                context.objectId(),
                context.toStatusId(),
                event.plan().templateId(),
                true,
                event.plan().tasks().size());
        events.append("task-generation:" + UUID.randomUUID(), "TASK_GENERATION_REQUESTED", context.objectId(), detail);
    }
}
