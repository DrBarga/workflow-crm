package dev.workflowcrm.objects.domain;

import jakarta.persistence.MappedSuperclass;
import java.time.LocalDate;
import java.util.Objects;

@MappedSuperclass
public abstract class ObjectLifecycle extends ObjectWorkflowState {
    public void schedule(ObjectMilestones milestones) {
        this.milestones = Objects.requireNonNull(milestones).calculated(constructionTermDays());
    }

    protected Integer constructionTermDays() {
        return null;
    }

    public ObjectMilestones getMilestones() {
        return milestones;
    }

    protected void initializeStatus(ObjectStatus initialStatus) {
        status = Objects.requireNonNull(initialStatus, "Initial status is required");
        if (!"FOUND".equals(status.getCode()) || !status.isActive()) {
            throw new IllegalArgumentException("A new object must have the active FOUND status");
        }
    }

    public ObjectStatus getStatus() {
        return status;
    }

    public void recordIcDecision(IcDecisionCommand command, ObjectStatusTransition transition) {
        IcDecisionMutation.apply(this, command, transition);
    }

    public void changeStatus(ObjectStatusTransition rule, ObjectStatusValues values) {
        ObjectStatusMutation.apply(this, rule, values);
    }

    public ObjectStatus getStatusBeforePause() {
        return statusBeforePause;
    }

    public ObjectStatus getStatusBeforeRefusal() {
        return statusBeforeRefusal;
    }

    public InvestmentCommitteeDecision getInvestmentCommitteeDecision() {
        return committee == null ? null : committee.result();
    }

    public LocalDate getInvestmentCommitteeDate() {
        return committee == null ? null : committee.date();
    }

    public String getInvestmentCommitteeConditions() {
        return committee == null ? null : committee.effectiveConditions();
    }

    public String getInvestmentCommitteeRefusalComment() {
        return committee == null ? null : committee.refusalComment();
    }

    public String getLegacyInvestmentCommitteeConditions() {
        return committee == null ? null : committee.legacyConditions();
    }

    public RefusalReason getRefusalReason() {
        return refusalReason;
    }

    public LocalDate getContractSignedDate() {
        return milestones == null ? null : milestones.contractSignedDate();
    }

    public LocalDate getOpeningDate() {
        return milestones == null ? null : milestones.openingActualDate();
    }
}
