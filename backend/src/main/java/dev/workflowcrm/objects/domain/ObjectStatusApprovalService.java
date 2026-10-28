package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectStatusApprovalService {
    private final ObjectStatusStore store;
    private final ObjectStatusAccess access;
    private final ObjectStatusExecutor executor;
    private final ObjectStatusFingerprint fingerprint;
    private final StatusApprovalRequests requests;
    private final ObjectStatusApprovalEvents events;
    private final ObjectTaskSetQueue taskSets;

    ObjectStatusApprovalService(
            ObjectStatusStore store,
            ObjectStatusAccess access,
            ObjectStatusExecutor executor,
            ObjectStatusFingerprint fingerprint,
            StatusApprovalRequests requests,
            ObjectStatusApprovalEvents events,
            ObjectTaskSetQueue taskSets) {
        this.store = store;
        this.access = access;
        this.executor = executor;
        this.fingerprint = fingerprint;
        this.requests = requests;
        this.events = events;
        this.taskSets = taskSets;
    }

    @Transactional
    public StatusApprovalRequests.Request get(UUID actor, UUID objectId, UUID id) {
        var object = store.lock(objectId);
        var request = request(object, id);
        access.require(actor, object);
        if (!actor.equals(request.initiatorId())) access.confirmer(actor, object, request.confirmingRoleId());
        return request;
    }

    @Transactional
    public ObjectStatusResult decide(UUID actor, UUID objectId, UUID id, String decision, String token) {
        return decide(actor, objectId, id, decision, token, java.util.Map.of());
    }

    @Transactional
    public ObjectStatusResult decide(
            UUID actor, UUID objectId, UUID id, String decision, String token, java.util.Map<UUID, UUID> assignees) {
        return execute(actor, objectId, id, decision, token, false, assignees);
    }

    @Transactional
    public ObjectStatusResult preview(UUID actor, UUID objectId, UUID id) {
        return preview(actor, objectId, id, java.util.Map.of());
    }

    @Transactional
    public ObjectStatusResult preview(UUID actor, UUID objectId, UUID id, java.util.Map<UUID, UUID> assignees) {
        return execute(actor, objectId, id, "APPROVED", null, true, assignees);
    }

    private ObjectStatusResult execute(
            UUID actor,
            UUID objectId,
            UUID id,
            String decision,
            String token,
            boolean preview,
            java.util.Map<UUID, UUID> assignees) {
        var object = store.lock(objectId);
        var request = request(object, id);
        if (!"PENDING".equals(request.status())) throw ObjectStatusExecutor.conflict();
        if ("CANCELLED".equals(decision)) {
            access.require(actor, object);
            if (!actor.equals(request.initiatorId())) throw new AccessDeniedException("Access denied");
        } else access.confirmer(actor, object, request.confirmingRoleId());
        ObjectStatusResult result;
        if ("APPROVED".equals(decision)) {
            if (object.getVersion() != request.objectVersion()) throw ObjectStatusExecutor.conflict();
            var command = fingerprint.decode(request.command());
            var selections = new java.util.TreeMap<>(command.assignees());
            selections.putAll(assignees);
            var rule = access.select(
                    access.require(request.initiatorId(), object),
                    object,
                    store.transitions(
                            object.getStatus().getId(),
                            command.status(),
                            command.status() == null
                                    ? (command.openingDate() != null ? "OPENING_DATE" : "CONTRACT_SIGNED_DATE")
                                    : null));
            if (!rule.getId().equals(request.transitionId())
                    || !rule.isConfirmationRequired()
                    || !rule.getConfirmingRoleId().equals(request.confirmingRoleId()))
                throw ObjectStatusExecutor.conflict();
            var confirmed = new ObjectStatusCommand(
                    command.status(),
                    command.version(),
                    command.refusalReasonId(),
                    command.contractSignedDate(),
                    command.contractTypeId(),
                    command.openingDate(),
                    command.openingPlannedDate(),
                    command.plannedDateProvided(),
                    token,
                    command.signing(),
                    selections);
            result = executor.run(
                    actor,
                    object,
                    rule,
                    confirmed,
                    true,
                    id,
                    request.initiatorId(),
                    ignored -> {},
                    preview ? ObjectStatusExecutor.PreviewMode.REQUIRED : ObjectStatusExecutor.PreviewMode.NONE);
            if (!result.saved()) return result;
        } else result = ObjectStatusExecutor.result(false, object, id, null, java.util.List.of());
        requests.finish(id, actor, decision);
        taskSets.approvalResolved(id, decision, result);
        events.resolved(request, actor, object.getVersion(), decision);
        return result;
    }

    private StatusApprovalRequests.Request request(RealEstateObject object, UUID id) {
        var request = requests.lock(id);
        if (!request.objectId().equals(object.getId())) throw new AccessDeniedException("Access denied");
        return request;
    }
}
