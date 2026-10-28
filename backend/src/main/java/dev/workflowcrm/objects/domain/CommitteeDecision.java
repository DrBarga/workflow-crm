package dev.workflowcrm.objects.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Embeddable
public record CommitteeDecision(
        @Enumerated(EnumType.STRING) @Column(name = "investment_committee_decision", length = 32)
                InvestmentCommitteeDecision result,
        @Column(name = "investment_committee_date") LocalDate date,
        @Column(name = "investment_committee_conditions", length = 50) String conditions,
        @Column(name = "investment_committee_refusal_comment", length = 50) String refusalComment,
        @Column(name = "legacy_investment_committee_conditions", columnDefinition = "text") String legacyConditions) {
    public CommitteeDecision {
        conditions = ObjectText.optional(conditions, 50, "IC conditions");
        refusalComment = ObjectText.optional(refusalComment, 50, "IC refusal comment");
    }

    public String effectiveConditions() {
        return conditions == null ? legacyConditions : conditions;
    }
}
