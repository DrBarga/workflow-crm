package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.events.EventOutbox;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusRequests {
    private final StatusApprovalRequests requests;
    private final ObjectStatusFingerprint fingerprint;
    private final ObjectStatusAudit audit;
    private final EventOutbox events;
    private final NotificationQueue notifications;
    private final ObjectAssignmentQuery users;
    private final ObjectStatusAccess access;

    ObjectStatusRequests(
            StatusApprovalRequests requests,
            ObjectStatusFingerprint fingerprint,
            ObjectStatusAudit audit,
            EventOutbox events,
            NotificationQueue notifications,
            ObjectAssignmentQuery users,
            ObjectStatusAccess access) {
        this.requests = requests;
        this.fingerprint = fingerprint;
        this.audit = audit;
        this.events = events;
        this.notifications = notifications;
        this.users = users;
        this.access = access;
    }

    UUID create(UUID actor, RealEstateObject object, ObjectStatusTransition rule, ObjectStatusCommand command) {
        var stored = new ObjectStatusCommand(
                command.status(),
                command.version(),
                command.refusalReasonId(),
                command.contractSignedDate(),
                command.contractTypeId(),
                command.openingDate(),
                command.openingPlannedDate(),
                command.plannedDateProvided(),
                null,
                command.signing(),
                command.assignees());
        var request = requests.create(
                object.getId(),
                actor,
                rule.getConfirmingRoleId(),
                rule.getId(),
                object.getVersion(),
                fingerprint.encode(stored));
        events.append("status-request:" + request.id(), "OBJECT_STATUS_APPROVAL_REQUESTED", object.getId(), request);
        audit.record(object.getId(), actor, object.getVersion(), "STATUS_REQUESTED", request);
        for (UUID recipient : users.activeMembers(rule.getConfirmingRoleId())) {
            try {
                access.confirmer(recipient, object, rule.getConfirmingRoleId());
            } catch (org.springframework.security.access.AccessDeniedException exception) {
                continue;
            }
            notifications.enqueue(
                    recipient,
                    "status-request:" + request.id(),
                    "OBJECT_STATUS_APPROVAL_REQUESTED",
                    object.getId(),
                    null);
        }
        return request.id();
    }
}
