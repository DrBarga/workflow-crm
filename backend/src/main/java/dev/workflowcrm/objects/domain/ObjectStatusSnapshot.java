package dev.workflowcrm.objects.domain;

import java.util.LinkedHashMap;
import java.util.Map;

final class ObjectStatusSnapshot {
    private ObjectStatusSnapshot() {}

    static Map<String, String> of(RealEstateObject object) {
        var values = new LinkedHashMap<String, String>();
        values.put("STATUS", object.getStatus().getCode());
        values.put(
                "STATUS_BEFORE_PAUSE",
                text(
                        object.getStatusBeforePause() == null
                                ? null
                                : object.getStatusBeforePause().getCode()));
        values.put(
                "STATUS_BEFORE_REFUSAL",
                text(
                        object.getStatusBeforeRefusal() == null
                                ? null
                                : object.getStatusBeforeRefusal().getCode()));
        values.put("IC_DECISION", text(object.getInvestmentCommitteeDecision()));
        values.put("IC_DATE", text(object.getInvestmentCommitteeDate()));
        values.put("IC_CONDITIONS", object.getInvestmentCommitteeConditions());
        values.put("IC_LEGACY_CONDITIONS", object.getLegacyInvestmentCommitteeConditions());
        values.put("IC_REFUSAL_COMMENT", object.getInvestmentCommitteeRefusalComment());
        values.put(
                "REFUSAL_REASON",
                text(
                        object.getRefusalReason() == null
                                ? null
                                : object.getRefusalReason().getCode()));
        values.put("CONTRACT_SIGNED_DATE", text(object.getContractSignedDate()));
        var dates = object.getMilestones();
        values.put(
                "PRELIMINARY_CONTRACT_SIGNED_DATE", text(dates == null ? null : dates.preliminaryContractSignedDate()));
        values.put("COMMISSIONING_DATE", text(dates == null ? null : dates.commissioningDate()));
        values.put(
                "CONSTRUCTION_ACCEPTANCE_PLANNED_DATE",
                text(dates == null ? null : dates.constructionAcceptancePlannedDate()));
        values.put(
                "CONSTRUCTION_TERM_DAYS",
                text(
                        object.getSpecifications() == null
                                        || object.getSpecifications().getCommercial() == null
                                ? null
                                : object.getSpecifications().getCommercial().constructionTermDays()));
        values.put("OPENING_DATE", text(object.getOpeningDate()));
        values.put(
                "OPENING_PLANNED_DATE",
                text(
                        object.getMilestones() == null
                                ? null
                                : object.getMilestones().openingPlannedDate()));
        values.put(
                "CONTRACT_TYPE",
                object.getSpecifications() == null
                        ? null
                        : object.getSpecifications().getContractType());
        return values;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
