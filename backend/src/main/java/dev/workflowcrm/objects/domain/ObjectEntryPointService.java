package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectEntryPointService {
    private final ObjectStatusStore store;
    private final ObjectEditStore edits;
    private final ObjectEditAccess access;
    private final UkraineBoundary boundary;
    private final EntryPointApprovalRequests requests;
    private final EntryPointApprovalRoute routes;
    private final EntryPointApprovalEvents events;
    private final ObjectEditWriter writer;

    ObjectEntryPointService(
            ObjectStatusStore store,
            ObjectEditStore edits,
            ObjectEditAccess access,
            UkraineBoundary boundary,
            EntryPointApprovalRequests requests,
            EntryPointApprovalRoute routes,
            EntryPointApprovalEvents events,
            ObjectEditWriter writer) {
        this.store = store;
        this.edits = edits;
        this.access = access;
        this.boundary = boundary;
        this.requests = requests;
        this.routes = routes;
        this.events = events;
        this.writer = writer;
    }

    @Transactional
    public ObjectEntryPointResult change(UUID actor, UUID id, EntranceCoordinates point) {
        access.requireEditor(actor);
        var object = store.lock(id);
        var member = access.require(actor, object);
        access.point(member);
        if (!boundary.contains(point))
            throw new UnprocessableInputException("entrance", "Entrance coordinates must be within Ukraine");
        if (same(object.getEntrance(), point)) return ObjectEntryPointResult.of(false, object, null);
        if (!access.pointApproval(object)) return apply(actor, object, point, null);
        var previous = requests.pending(id);
        if (previous != null) {
            if (previous.initiatorId().equals(actor)
                    && previous.objectVersion() == object.getVersion()
                    && previous.longitude().compareTo(point.getLongitude()) == 0
                    && previous.latitude().compareTo(point.getLatitude()) == 0)
                return ObjectEntryPointResult.of(false, object, previous.id());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object already has a pending entry point request");
        }
        var route = routes.resolve(member, object);
        var request = requests.create(
                id,
                actor,
                route.roleId(),
                route.userId(),
                point.getLongitude(),
                point.getLatitude(),
                object.getVersion());
        events.created(request, route);
        return ObjectEntryPointResult.of(false, object, request.id());
    }

    @Transactional
    public ObjectEntryPointResult decide(UUID actor, UUID objectId, UUID id, String decision) {
        access.requireEditor(actor);
        var object = store.lock(objectId);
        var request = requests.lock(id);
        if (!request.objectId().equals(objectId)) throw ObjectEditAccess.denied();
        if (decision.equals("CANCELLED")) {
            access.require(actor, object);
            if (!actor.equals(request.initiatorId())) throw ObjectEditAccess.denied();
        } else routes.require(actor, object, request);
        if (!request.status().equals("PENDING") || request.objectVersion() != object.getVersion())
            throw new ObjectEditConflictException(object.getVersion());
        var result = decision.equals("APPROVED")
                ? apply(actor, object, new EntranceCoordinates(request.longitude(), request.latitude()), id)
                : ObjectEntryPointResult.of(false, object, id);
        requests.finish(id, actor, decision);
        events.resolved(request, actor, object.getVersion(), decision);
        return result;
    }

    private ObjectEntryPointResult apply(UUID actor, RealEstateObject object, EntranceCoordinates point, UUID request) {
        if (!boundary.contains(point))
            throw new UnprocessableInputException("entrance", "Entrance coordinates must be within Ukraine");
        var before = edits.snapshot(object);
        object.moveEntrance(point);
        writer.save(actor, object, before, "ENTRY_POINT_CHANGED", request);
        return ObjectEntryPointResult.of(true, object, request);
    }

    private boolean same(EntranceCoordinates first, EntranceCoordinates second) {
        return first.getLongitude().compareTo(second.getLongitude()) == 0
                && first.getLatitude().compareTo(second.getLatitude()) == 0;
    }
}
