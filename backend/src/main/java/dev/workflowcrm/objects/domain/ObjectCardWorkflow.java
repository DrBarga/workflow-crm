package dev.workflowcrm.objects.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.workflowcrm.objects.api.ObjectRefusalTag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ObjectCardWorkflow(
        String statusBeforePause,
        String statusBeforeRefusal,
        InvestmentCommitteeDecision investmentCommitteeDecision,
        LocalDate investmentCommitteeDate,
        String investmentCommitteeConditions,
        String investmentCommitteeRefusalComment,
        ObjectRefusalTag refusalTag,
        @JsonInclude(JsonInclude.Include.NON_NULL) String contractType,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID contractTypeId,
        ObjectMilestones milestones,
        Instant updatedAt) {
    static ObjectCardWorkflow of(RealEstateObject object, boolean contractVisible) {
        var w = ObjectListWorkflow.of(object);
        return new ObjectCardWorkflow(
                w.statusBeforePause(),
                w.statusBeforeRefusal(),
                w.investmentCommitteeDecision(),
                w.investmentCommitteeDate(),
                w.investmentCommitteeConditions(),
                w.investmentCommitteeRefusalComment(),
                w.refusalTag(),
                contractVisible ? w.contractType() : null,
                contractVisible ? w.contractTypeId() : null,
                w.milestones(),
                w.updatedAt());
    }
}
