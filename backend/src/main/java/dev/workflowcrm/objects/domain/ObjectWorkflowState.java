package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.*;

@MappedSuperclass
public abstract class ObjectWorkflowState extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    protected ObjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_before_pause_id")
    protected ObjectStatus statusBeforePause;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_before_refusal_id")
    protected ObjectStatus statusBeforeRefusal;

    @Embedded
    protected CommitteeDecision committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refusal_reason_id")
    protected RefusalReason refusalReason;

    @Embedded
    protected ObjectMilestones milestones;
}
