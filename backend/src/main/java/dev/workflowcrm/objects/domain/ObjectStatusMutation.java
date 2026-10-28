package dev.workflowcrm.objects.domain;

final class ObjectStatusMutation {
    private ObjectStatusMutation() {}

    static void apply(ObjectLifecycle object, ObjectStatusTransition rule, ObjectStatusValues values) {
        var effects = rule.getEffects();
        if (effects.pauseEntry()) object.statusBeforePause = object.status;
        if (effects.refusalEntry()) {
            object.statusBeforeRefusal = effects.pauseExit() ? object.statusBeforePause : object.status;
            object.refusalReason = values.refusalReason();
        }
        if (effects.refusalExit()) {
            object.statusBeforeRefusal = null;
            object.refusalReason = null;
            if (object.getInvestmentCommitteeDecision() == InvestmentCommitteeDecision.REFUSED) object.committee = null;
        }
        if (effects.pauseExit()) object.statusBeforePause = null;
        if (effects.icReset()) object.committee = null;
        object.milestones = values.milestones();
        object.status = rule.getToStatus();
    }
}
