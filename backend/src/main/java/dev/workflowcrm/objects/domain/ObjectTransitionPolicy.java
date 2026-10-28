package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

@MappedSuperclass
public abstract class ObjectTransitionPolicy extends BaseEntity {
    @Column(name = "is_confirmation_required", nullable = false)
    private boolean confirmationRequired;

    @Column(name = "confirming_role_id")
    private UUID confirmingRoleId;

    @Column(name = "is_system_only", nullable = false)
    private boolean systemOnly;

    @Column(name = "is_rollback", nullable = false)
    private boolean rollback;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_committee_decision", length = 32)
    private InvestmentCommitteeDecision investmentCommitteeDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_code", length = 32)
    private TransitionCondition condition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_refusal_reason_id")
    private RefusalReason fixedRefusalReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "unfinished_task_action", nullable = false, length = 16)
    private UnfinishedTaskAction unfinishedTaskAction = UnfinishedTaskAction.NONE;

    @Column(name = "is_unavailable_after_task_creation", nullable = false)
    private boolean unavailableAfterTaskCreation;

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public UUID getConfirmingRoleId() {
        return confirmingRoleId;
    }

    public boolean isSystemOnly() {
        return systemOnly;
    }

    public boolean isRollback() {
        return rollback;
    }

    public boolean isActive() {
        return active;
    }

    public InvestmentCommitteeDecision getInvestmentCommitteeDecision() {
        return investmentCommitteeDecision;
    }

    public TransitionCondition getCondition() {
        return condition;
    }

    public RefusalReason getFixedRefusalReason() {
        return fixedRefusalReason;
    }

    public UnfinishedTaskAction getUnfinishedTaskAction() {
        return unfinishedTaskAction;
    }

    public boolean isUnavailableAfterTaskCreation() {
        return unavailableAfterTaskCreation;
    }
}
