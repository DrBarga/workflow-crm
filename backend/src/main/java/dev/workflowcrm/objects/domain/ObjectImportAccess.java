package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ObjectImportAccess {
    private final ObjectAssignmentQuery users;

    public ObjectImportAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    public void require(UUID actor) {
        var user = users.member(actor).orElseThrow(() -> new AccessDeniedException("Import access denied"));
        if (!user.active() || !"SUPERADMIN".equals(user.role()))
            throw new AccessDeniedException("Import access denied");
    }
}
