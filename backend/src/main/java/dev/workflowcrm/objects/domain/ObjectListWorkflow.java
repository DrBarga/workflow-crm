package dev.workflowcrm.objects.domain;

import dev.workflowcrm.objects.api.ObjectRefusalTag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ObjectListWorkflow(
        String statusBeforePause,
        String statusBeforeRefusal,
        InvestmentCommitteeDecision investmentCommitteeDecision,
        LocalDate investmentCommitteeDate,
        String investmentCommitteeConditions,
        String investmentCommitteeRefusalComment,
        ObjectRefusalTag refusalTag,
        String contractType,
        UUID contractTypeId,
        ObjectMilestones milestones,
        Instant updatedAt) {
    public static ObjectListWorkflow of(RealEstateObject o) {
        var specifications = o.getSpecifications();
        var type = specifications == null ? null : specifications.getContractTypeReference();
        var refusal = o.getRefusalReason();
        return new ObjectListWorkflow(
                o.getStatusBeforePause() == null
                        ? null
                        : o.getStatusBeforePause().getCode(),
                o.getStatusBeforeRefusal() == null
                        ? null
                        : o.getStatusBeforeRefusal().getCode(),
                o.getInvestmentCommitteeDecision(),
                o.getInvestmentCommitteeDate(),
                o.getInvestmentCommitteeConditions(),
                o.getInvestmentCommitteeRefusalComment(),
                refusal == null ? null : new ObjectRefusalTag(refusal.getId(), refusal.getCode(), refusal.getName()),
                specifications == null ? null : specifications.getContractType(),
                type == null ? null : type.getId(),
                o.getMilestones(),
                o.getUpdatedAt());
    }
}
