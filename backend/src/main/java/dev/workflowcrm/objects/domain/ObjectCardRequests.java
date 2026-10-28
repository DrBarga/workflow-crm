package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.ObjectApprovalQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectCardRequests {
    private final ObjectApprovalQuery query;

    ObjectCardRequests(ObjectApprovalQuery query) {
        this.query = query;
    }

    List<ObjectCardRequest> pending(UUID objectId) {
        return query.pending(objectId).stream()
                .map(r -> new ObjectCardRequest(
                        r.id(),
                        r.type(),
                        new ObjectCardRequest.Requested(
                                r.requestedStatus(),
                                r.longitude() == null
                                        ? null
                                        : new CreatedObjectView.Coordinates(r.longitude(), r.latitude())),
                        r.initiatorId(),
                        r.confirmingRoleId(),
                        r.confirmingUserId(),
                        r.createdAt()))
                .toList();
    }
}
