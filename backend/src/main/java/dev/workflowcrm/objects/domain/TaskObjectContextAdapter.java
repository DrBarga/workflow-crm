package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TaskObjectContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class TaskObjectContextAdapter implements TaskObjectContext {
    private final ObjectStatusStore objects;
    private final ObjectDeletionAvailability deletions;

    TaskObjectContextAdapter(ObjectStatusStore objects, ObjectDeletionAvailability deletions) {
        this.objects = objects;
        this.deletions = deletions;
    }

    public State lock(UUID id) {
        var object = objects.lock(id);
        if (deletions.pending(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object deletion is in progress");
        return new State(
                id,
                object.getRegionId(),
                object.getStatus().getCode(),
                object.getStatus().isFinal());
    }
}
