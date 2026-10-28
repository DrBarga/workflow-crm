package dev.workflowcrm.objects.domain;

import java.time.LocalDate;
import java.util.Set;

public record ObjectSigningDetails(
        LocalDate preliminaryContractSignedDate,
        LocalDate commissioningDate,
        LocalDate constructionAcceptancePlannedDate,
        Integer constructionTermDays,
        Set<String> fields) {
    public ObjectSigningDetails {
        fields = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(fields));
    }

    public <T> T value(String field, T proposed, T current) {
        return fields.contains(field) ? proposed : current;
    }

    static ObjectSigningDetails prepared(ObjectMilestones dates, CommercialTerms commercial) {
        return new ObjectSigningDetails(
                dates.preliminaryContractSignedDate(),
                dates.commissioningDate(),
                dates.constructionAcceptancePlannedDate(),
                commercial == null ? null : commercial.constructionTermDays(),
                Set.of(
                        "preliminaryContractSignedDate",
                        "commissioningDate",
                        "constructionAcceptancePlannedDate",
                        "constructionTermDays"));
    }
}
