package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TransitionTaskRequirements;
import java.util.List;
import java.util.UUID;

public record ObjectCardTransition(
        UUID id,
        String code,
        String status,
        String statusName,
        boolean confirmationRequired,
        UUID confirmingRoleId,
        List<String> requiredFields,
        UnfinishedTaskAction unfinishedTaskAction,
        List<String> requiredCompletedTaskCodes,
        ObjectTransitionConditions unmetConditions) {
    static ObjectCardTransition of(
            ObjectStatusTransition r, RealEstateObject object, TransitionTaskRequirements.Assessment tasks) {
        var specs = object.getSpecifications();
        var values = new ObjectStatusValues(
                object.getRefusalReason(),
                specs == null ? null : specs.getContractTypeReference(),
                object.getMilestones(),
                specs == null ? null : specs.getCommercial());
        return new ObjectCardTransition(
                r.getId(),
                r.getCode(),
                r.getToStatus().getCode(),
                r.getToStatus().getName(),
                r.isConfirmationRequired(),
                r.getConfirmingRoleId(),
                r.getRequiredFields().stream()
                        .map(ObjectTransitionFields::field)
                        .sorted()
                        .toList(),
                r.getUnfinishedTaskAction(),
                r.getRequiredTaskCodes().stream().sorted().toList(),
                ObjectTransitionConditions.of(
                        ObjectTransitionFields.missing(object, r, values, false), r.getRequiredTaskCodes(), tasks));
    }
}
