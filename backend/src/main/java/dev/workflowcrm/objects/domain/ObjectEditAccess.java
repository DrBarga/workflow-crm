package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectEditAccess {
    private static final Set<String> EDITORS =
            Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN", "CONTRACT_MANAGER");
    private final ObjectStatusAccess scope;
    private final ObjectAssignmentQuery users;
    private final ObjectFieldPolicy fields;
    private final ObjectCardStore card;
    private final ObjectTaskTransition tasks;

    ObjectEditAccess(
            ObjectStatusAccess scope,
            ObjectAssignmentQuery users,
            ObjectFieldPolicy fields,
            ObjectCardStore card,
            ObjectTaskTransition tasks) {
        this.scope = scope;
        this.users = users;
        this.fields = fields;
        this.card = card;
        this.tasks = tasks;
    }

    void requireEditor(UUID actor) {
        if (users.member(actor)
                .filter(ObjectAssignmentQuery.Member::active)
                .filter(m -> EDITORS.contains(m.role()))
                .isEmpty()) throw denied();
    }

    void requireContractFields(UUID actor, Set<String> supplied) {
        var member = users.member(actor).orElseThrow(ObjectEditAccess::denied);
        if (member.role().equals("CONTRACT_MANAGER")
                && supplied.stream()
                        .anyMatch(f -> !Set.of("version", "confirmationToken").contains(f)
                                && !ObjectFieldPolicy.CONTRACT_FIELDS.contains(f))) throw denied();
    }

    ObjectAssignmentQuery.Member require(UUID actor, RealEstateObject object) {
        requireEditor(actor);
        return scope.require(actor, object);
    }

    void patch(ObjectAssignmentQuery.Member actor, RealEstateObject object, Set<String> supplied) {
        if (actor.role().equals("CONTRACT_MANAGER")
                && supplied.stream().anyMatch(f -> !ObjectFieldPolicy.CONTRACT_FIELDS.contains(f))) throw denied();
        if (actor.role().equals("MANAGER") && supplied.contains("commissioningDate")) throw denied();
        var editable = fields.editable(actor, tasks.hasContractTypeTaskSet(object.getId()));
        supplied.stream().sorted().forEach(f -> {
            if (!Boolean.TRUE.equals(editable.get(f)))
                throw new UnprocessableInputException(f, "Field cannot be edited by this role at this stage");
        });
    }

    void point(ObjectAssignmentQuery.Member actor) {
        if (!Set.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR").contains(actor.role())) throw denied();
    }

    boolean pointApproval(RealEstateObject object) {
        return fields.effectiveOrder(object) >= card.preparationOrder();
    }

    static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }
}
