package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TransitionTaskRequirements;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ObjectCardTransitions {
    private final ObjectStatusAccess access;
    private final ObjectCardStore store;

    private final TransitionTaskRequirements tasks;

    ObjectCardTransitions(ObjectStatusAccess access, ObjectCardStore store, TransitionTaskRequirements tasks) {
        this.access = access;
        this.store = store;
        this.tasks = tasks;
    }

    List<ObjectCardTransition> available(
            ObjectAssignmentQuery.Member actor, RealEstateObject object, boolean hasTasks) {
        var rules = store.transitions(object.getStatus().getId());
        var available = rules.stream()
                .filter(r -> !r.isSystemOnly() && r.getRoleIds().contains(actor.roleId()))
                .filter(r -> !r.isUnavailableAfterTaskCreation() || !hasTasks)
                .filter(r -> access.applicable(object, r))
                .filter(r -> rules.stream()
                                .filter(other -> !other.isSystemOnly()
                                        && other.getRoleIds().contains(actor.roleId())
                                        && other.getToStatus()
                                                .getId()
                                                .equals(r.getToStatus().getId())
                                        && access.applicable(object, other))
                                .count()
                        == 1)
                .toList();
        var codes = available.stream()
                .flatMap(r -> r.getRequiredTaskCodes().stream())
                .collect(Collectors.toSet());
        var requirements = tasks.inspect(object.getId(), object.getStatus().getId(), codes);
        return available.stream()
                .map(r -> ObjectCardTransition.of(r, object, requirements))
                .toList();
    }
}
