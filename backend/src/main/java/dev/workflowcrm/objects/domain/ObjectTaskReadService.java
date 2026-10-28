package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.read.ReadPage;
import dev.workflowcrm.tasks.api.ObjectTaskRead;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ObjectTaskReadService {
    private final ObjectCardAccess access;
    private final ObjectCardStore objects;
    private final ObjectTaskRead tasks;

    ObjectTaskReadService(ObjectCardAccess access, ObjectCardStore objects, ObjectTaskRead tasks) {
        this.access = access;
        this.objects = objects;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectTaskRead.Page list(UUID actor, UUID objectId, UUID setId, int page, int size) {
        var member = access.require(actor);
        var object = objects.summary(objectId);
        if (!access.full(member, object) && !"CONTRACT_MANAGER".equals(member.role()))
            throw new AccessDeniedException("Object tasks are unavailable");
        return tasks.list(objectId, setId, new ReadPage(page, size));
    }
}
