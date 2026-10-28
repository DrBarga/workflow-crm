package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectContractTypePolicy {
    private final ObjectStatusAccess access;
    private final ObjectFieldPolicy fields;
    private final ObjectTaskTransition tasks;

    ObjectContractTypePolicy(ObjectStatusAccess access, ObjectFieldPolicy fields, ObjectTaskTransition tasks) {
        this.access = access;
        this.fields = fields;
        this.tasks = tasks;
    }

    void require(UUID actor, RealEstateObject object, UUID type) {
        if (type == null) return;
        var previous = object.getSpecifications() == null
                ? null
                : object.getSpecifications().getContractTypeReference();
        if (previous != null && previous.getId().equals(type)) return;
        var member = access.require(actor, object);
        if (!fields.contractEditable(member.role(), tasks.hasContractTypeTaskSet(object.getId())))
            throw new UnprocessableInputException(
                    "contractTypeId", "Contract type cannot be changed by this role at this stage");
    }
}
