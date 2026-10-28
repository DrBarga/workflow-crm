package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.ObjectTaskTransition;
import java.time.LocalDate;

final class ObjectStatusTaskContext {
    private ObjectStatusTaskContext() {}

    static ObjectTaskTransition.Context of(
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            ObjectStatusValues values,
            LocalDate triggerDate) {
        return of(object, rule, command, values, triggerDate, null);
    }

    static ObjectTaskTransition.Context of(
            RealEstateObject object,
            ObjectStatusTransition rule,
            ObjectStatusCommand command,
            ObjectStatusValues values,
            LocalDate triggerDate,
            java.util.UUID confirmingUserId) {
        boolean resume = rule.getEffects().pauseExit()
                && object.getStatusBeforePause() != null
                && object.getStatusBeforePause()
                        .getId()
                        .equals(rule.getToStatus().getId());
        var specifications = object.getSpecifications();
        var currentType = specifications == null ? null : specifications.getContractTypeReference();
        String legacyType = specifications == null ? null : specifications.getLegacyContractType();
        if (command.contractTypeId() != null
                && (currentType == null || !command.contractTypeId().equals(currentType.getId()))) legacyType = null;
        return new ObjectTaskTransition.Context(
                object.getId(),
                object.getManagerId(),
                object.getRegionId(),
                object.getStatus().getId(),
                rule.getToStatus().getId(),
                values.contractType() == null
                        ? specifications == null ? null : specifications.getContractType()
                        : values.contractType().getCode(),
                triggerDate,
                !resume && !rule.getEffects().pauseEntry(),
                legacyType,
                rule.getUnfinishedTaskAction() == UnfinishedTaskAction.CANCEL_ALL,
                rule.getUnfinishedTaskAction() == UnfinishedTaskAction.CANCEL,
                command.assignees(),
                confirmingUserId);
    }
}
