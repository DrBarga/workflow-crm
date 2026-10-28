package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.*;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectTaskPreviewService {
    private final ObjectCardAccess access;
    private final ObjectCardStore objects;
    private final ObjectStatusStore statuses;
    private final ObjectTaskTransition tasks;
    private final ObjectTaskTriggerDate triggerDate;

    ObjectTaskPreviewService(
            ObjectCardAccess access,
            ObjectCardStore objects,
            ObjectStatusStore statuses,
            ObjectTaskTransition tasks,
            ObjectTaskTriggerDate triggerDate) {
        this.access = access;
        this.objects = objects;
        this.statuses = statuses;
        this.tasks = tasks;
        this.triggerDate = triggerDate;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TaskSetPreview preview(UUID actor, UUID id, String status) {
        var member = access.require(actor);
        var summary = objects.summary(id);
        if (!access.full(member, summary)) throw new AccessDeniedException("Access denied");
        var object = objects.full(id);
        var target = statuses.status(status);
        var specifications = object.getSpecifications();
        return tasks.previewSet(new ObjectTaskTransition.Context(
                id,
                object.getManagerId(),
                object.getRegionId(),
                object.getStatus().getId(),
                target.getId(),
                specifications == null ? null : specifications.getContractType(),
                triggerDate.today(),
                true));
    }
}
