package dev.workflowcrm.objects.domain;

import java.util.List;

final class ObjectTransitionFields {
    private ObjectTransitionFields() {}

    static List<String> missing(
            RealEstateObject object, ObjectStatusTransition rule, ObjectStatusValues values, boolean refusalProvided) {
        return rule.getRequiredFields().stream()
                .filter(field -> !present(object, values, refusalProvided, field))
                .map(ObjectTransitionFields::field)
                .sorted()
                .toList();
    }

    static String field(TransitionRequiredField required) {
        return switch (required) {
            case REFUSAL_REASON -> "refusalReasonId";
            case CONTRACT_SIGNED_DATE -> "contractSignedDate";
            case OPENING_DATE -> "openingDate";
            case CONTRACT_TYPE -> "contractTypeId";
            case COMMISSIONING_DATE -> "commissioningDate";
            case CONSTRUCTION_ACCEPTANCE_PLANNED_DATE -> "constructionAcceptancePlannedDate";
            case CONSTRUCTION_TERM_DAYS -> "constructionTermDays";
            case OPENING_PLANNED_DATE -> "openingPlannedDate";
            case INVESTMENT_COMMITTEE_DATE -> "investmentCommitteeDate";
            case INVESTMENT_COMMITTEE_CONDITIONS -> "investmentCommitteeConditions";
        };
    }

    private static boolean present(
            RealEstateObject object,
            ObjectStatusValues values,
            boolean refusalProvided,
            TransitionRequiredField field) {
        var dates = values.milestones();
        return switch (field) {
            case REFUSAL_REASON -> refusalProvided && values.refusalReason() != null;
            case CONTRACT_TYPE ->
                values.contractType() != null && values.contractType().isActive();
            case CONTRACT_SIGNED_DATE -> dates != null && dates.contractSignedDate() != null;
            case OPENING_DATE -> dates != null && dates.openingActualDate() != null;
            case COMMISSIONING_DATE -> dates != null && dates.commissioningDate() != null;
            case CONSTRUCTION_ACCEPTANCE_PLANNED_DATE ->
                dates != null && dates.constructionAcceptancePlannedDate() != null;
            case CONSTRUCTION_TERM_DAYS ->
                values.commercial() != null && values.commercial().constructionTermDays() != null;
            case OPENING_PLANNED_DATE -> dates != null && dates.openingPlannedDate() != null;
            case INVESTMENT_COMMITTEE_DATE -> object.getInvestmentCommitteeDate() != null;
            case INVESTMENT_COMMITTEE_CONDITIONS -> object.getInvestmentCommitteeConditions() != null;
        };
    }
}
