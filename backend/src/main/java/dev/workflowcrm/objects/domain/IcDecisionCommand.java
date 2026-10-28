package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.time.LocalDate;

public record IcDecisionCommand(
        InvestmentCommitteeDecision decision,
        LocalDate date,
        String conditions,
        String refusalComment,
        long version,
        String confirmationToken,
        java.util.Map<java.util.UUID, java.util.UUID> assignees) {
    public IcDecisionCommand(
            InvestmentCommitteeDecision decision,
            LocalDate date,
            String conditions,
            String refusalComment,
            long version,
            String confirmationToken) {
        this(decision, date, conditions, refusalComment, version, confirmationToken, java.util.Map.of());
    }

    public IcDecisionCommand(
            InvestmentCommitteeDecision decision,
            LocalDate date,
            String conditions,
            long version,
            String confirmationToken) {
        this(decision, date, conditions, null, version, confirmationToken);
    }

    public IcDecisionCommand {
        assignees = assignees == null
                ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(java.util.Map.copyOf(assignees)));
        if (decision == null) throw new UnprocessableInputException("decision", "Choose an IC decision");
        if (date == null) throw new UnprocessableInputException("date", "IC date is required");
        if (version < 0) throw new UnprocessableInputException("version", "Version must not be negative");
        conditions = conditions == null ? null : conditions.strip();
        if (conditions != null && conditions.isEmpty()) conditions = null;
        if (conditions != null && conditions.codePointCount(0, conditions.length()) > 50)
            throw new UnprocessableInputException("conditions", "Conditions must not exceed 50 characters");
        if (decision == InvestmentCommitteeDecision.APPROVED_WITH_CONDITIONS && conditions == null)
            throw new UnprocessableInputException("conditions", "IC conditions are required");
        refusalComment = refusalComment == null
                ? null
                : refusalComment.replace('\u00a0', ' ').replace('\u202f', ' ').strip();
        if (refusalComment != null && refusalComment.isEmpty()) refusalComment = null;
        if (refusalComment != null && refusalComment.codePointCount(0, refusalComment.length()) > 50)
            throw new UnprocessableInputException("refusalComment", "Refusal comment must not exceed 50 characters");
        if (decision == InvestmentCommitteeDecision.REFUSED && refusalComment == null)
            throw new UnprocessableInputException("refusalComment", "Refusal comment is required");
        if (refusalComment != null && decision != InvestmentCommitteeDecision.REFUSED)
            throw new UnprocessableInputException("refusalComment", "Refusal comment applies only to refusal");
        if (decision != InvestmentCommitteeDecision.APPROVED_WITH_CONDITIONS && conditions != null)
            throw new UnprocessableInputException("conditions", "Conditions apply only to conditional approval");
    }
}
