package dev.workflowcrm.objects.domain;

public record ObjectStatusValues(
        RefusalReason refusalReason,
        ContractType contractType,
        ObjectMilestones milestones,
        CommercialTerms commercial) {
    public ObjectStatusValues(RefusalReason refusalReason, ContractType contractType, ObjectMilestones milestones) {
        this(refusalReason, contractType, milestones, null);
    }
}
