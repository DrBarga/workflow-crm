package dev.workflowcrm.objects.domain;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class EntryPointApprovalRoute {
    record Route(UUID roleId, UUID userId, List<UUID> recipients) {}

    private final ObjectAssignmentQuery users;
    private final ObjectEditAccess access;

    EntryPointApprovalRoute(ObjectAssignmentQuery users, ObjectEditAccess access) {
        this.users = users;
        this.access = access;
    }

    Route resolve(ObjectAssignmentQuery.Member actor, RealEstateObject object) {
        if (actor.role().equals("MANAGER")) {
            var leads = members("REGIONAL_MANAGER").stream()
                    .filter(m -> m.belongsTo(object.getRegionId()))
                    .toList();
            if (!leads.isEmpty())
                return new Route(
                        leads.getFirst().roleId(),
                        null,
                        leads.stream().map(ObjectAssignmentQuery.Member::id).toList());
        } else {
            var manager = users.member(object.getManagerId())
                    .filter(ObjectAssignmentQuery.Member::active)
                    .filter(m -> m.role().equals("MANAGER") && m.belongsTo(object.getRegionId()));
            if (manager.isPresent())
                return new Route(null, manager.get().id(), List.of(manager.get().id()));
        }
        var directors = members("DEVELOPMENT_DIRECTOR").stream()
                .filter(m -> !m.id().equals(actor.id()))
                .toList();
        if (directors.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active approver is available");
        return new Route(
                null, directors.getFirst().id(), List.of(directors.getFirst().id()));
    }

    void require(UUID actor, RealEstateObject object, EntryPointApprovalRequests.Request request) {
        var member = access.require(actor, object);
        if (actor.equals(request.initiatorId())) throw ObjectEditAccess.denied();
        boolean permitted = request.confirmingUserId() != null
                ? actor.equals(request.confirmingUserId())
                : member.role().equals("REGIONAL_MANAGER") && member.roleId().equals(request.confirmingRoleId());
        if (!permitted) throw ObjectEditAccess.denied();
        var initiator = access.require(request.initiatorId(), object);
        access.point(initiator);
        var expected = resolve(initiator, object);
        if (!Objects.equals(expected.roleId(), request.confirmingRoleId())
                || !Objects.equals(expected.userId(), request.confirmingUserId()))
            throw new ObjectEditConflictException(object.getVersion());
    }

    private List<ObjectAssignmentQuery.Member> members(String role) {
        return users.activeMembers(role).stream()
                .sorted()
                .map(users::member)
                .flatMap(Optional::stream)
                .filter(ObjectAssignmentQuery.Member::active)
                .toList();
    }
}
