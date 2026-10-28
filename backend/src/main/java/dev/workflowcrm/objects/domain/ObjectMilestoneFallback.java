package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectMilestoneFallback {
    private final ObjectEditStore edits;
    private final ObjectEditWriter writer;

    ObjectMilestoneFallback(ObjectEditStore edits, ObjectEditWriter writer) {
        this.edits = edits;
        this.writer = writer;
    }

    ObjectStatusResult save(UUID actor, RealEstateObject object, ObjectTransitionConditionsException blocked) {
        var before = edits.snapshot(object);
        var values = blocked.values();
        var specifications = object.getSpecifications();
        object.updateSpecifications(specifications == null ? null : specifications.getTechnical(), values.commercial());
        if (values.contractType() != null
                && (specifications == null
                        || specifications.getContractTypeReference() == null
                        || !values.contractType()
                                .getId()
                                .equals(specifications
                                        .getContractTypeReference()
                                        .getId()))) object.changeContractType(values.contractType());
        object.schedule(values.milestones());
        boolean saved = writer.save(actor, object, before, "FIELDS_CHANGED", null);
        return ObjectStatusExecutor.result(saved, object, null, null, List.of()).withConditions(blocked.conditions());
    }
}
