package dev.workflowcrm.objects.domain;

import java.util.Objects;

final class IcDecisionMutation {
    private IcDecisionMutation() {}

    static void apply(ObjectLifecycle object, IcDecisionCommand command, ObjectStatusTransition transition) {
        if (!"IC_PREPARATION".equals(object.status.getCode())
                || !transition.isActive()
                || !transition.isSystemOnly()
                || transition.getInvestmentCommitteeDecision() != command.decision()
                || !transition.getFromStatus().getId().equals(object.status.getId()))
            throw new IllegalArgumentException("Invalid IC transition");
        object.committee = new CommitteeDecision(
                command.decision(), command.date(), command.conditions(), command.refusalComment(), null);
        if (command.decision() == InvestmentCommitteeDecision.REFUSED) {
            object.statusBeforeRefusal = object.status;
            object.refusalReason =
                    Objects.requireNonNull(transition.getFixedRefusalReason(), "IC refusal reason is required");
            if (!"IC_NOT_APPROVED".equals(object.refusalReason.getCode()) || !object.refusalReason.isSystemOnly())
                throw new IllegalStateException("Invalid system IC refusal reason");
        } else {
            object.refusalReason = null;
            object.statusBeforeRefusal = null;
        }
        object.status = transition.getToStatus();
    }
}
