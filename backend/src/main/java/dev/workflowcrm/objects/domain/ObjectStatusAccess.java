package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectStatusAccess {
    private final ObjectAssignmentQuery users;

    ObjectStatusAccess(ObjectAssignmentQuery users) {
        this.users = users;
    }

    ObjectAssignmentQuery.Member require(UUID actor, RealEstateObject object) {
        var member = users.member(actor)
                .filter(ObjectAssignmentQuery.Member::active)
                .orElseThrow(ObjectStatusAccess::denied);
        boolean scope =
                switch (member.role()) {
                    case "MANAGER" -> actor.equals(object.getManagerId()) && member.belongsTo(object.getRegionId());
                    case "REGIONAL_MANAGER" -> member.belongsTo(object.getRegionId());
                    case "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "SUPERADMIN", "CEO" -> true;
                    default -> false;
                };
        if (!scope) throw denied();
        return member;
    }

    ObjectStatusTransition select(
            ObjectAssignmentQuery.Member actor, RealEstateObject object, List<ObjectStatusTransition> rules) {
        if (rules.isEmpty()) throw new UnprocessableInputException("status", "Transition is not configured");
        var permitted = rules.stream()
                .filter(r -> !r.isSystemOnly() && r.getRoleIds().contains(actor.roleId()))
                .toList();
        if (permitted.isEmpty()) {
            if (rules.stream()
                    .anyMatch(r -> !r.isSystemOnly() && r.getValidationRoleIds().contains(actor.roleId())))
                throw new UnprocessableInputException(
                        "status", "Transition is unavailable for this role at this stage");
            throw denied();
        }
        var applicable = permitted.stream().filter(r -> applicable(object, r)).toList();
        if (applicable.isEmpty())
            throw new UnprocessableInputException("status", "Transition condition is not satisfied");
        if (applicable.size() != 1)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Transition configuration is ambiguous");
        return applicable.getFirst();
    }

    void confirmer(UUID actor, RealEstateObject object, UUID confirmingRole) {
        var member = require(actor, object);
        if (!confirmingRole.equals(member.roleId())) throw denied();
    }

    void requireEditable(ObjectAssignmentQuery.Member actor, List<ObjectStatusTransition> rules) {
        if (rules.stream().noneMatch(r -> !r.isSystemOnly() && r.getRoleIds().contains(actor.roleId()))) throw denied();
    }

    boolean applicable(RealEstateObject object, ObjectStatusTransition rule) {
        if (rule.getCondition() == null) return true;
        return switch (rule.getCondition()) {
            case RESUME_PREVIOUS_STATUS -> same(object.getStatusBeforePause(), rule.getToStatus());
            case NOT_RESUME_PREVIOUS_STATUS -> !same(object.getStatusBeforePause(), rule.getToStatus());
            case EARLY_STATUS_BEFORE_PAUSE, CONTRACT_STATUS_BEFORE_PAUSE ->
                contains(rule, object.getStatusBeforePause());
            case EARLY_STATUS_BEFORE_REFUSAL -> contains(rule, object.getStatusBeforeRefusal());
            case RESUME_EARLY_REFUSED ->
                same(object.getStatusBeforePause(), rule.getToStatus())
                        && contains(rule, object.getStatusBeforeRefusal());
        };
    }

    private boolean contains(ObjectStatusTransition rule, ObjectStatus value) {
        return value != null && rule.getConditionStatusIds().contains(value.getId());
    }

    private boolean same(ObjectStatus first, ObjectStatus second) {
        return first != null && first.getId().equals(second.getId());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
