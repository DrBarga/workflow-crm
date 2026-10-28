package dev.workflowcrm.objects.domain;

import java.time.LocalDate;
import java.util.UUID;

public record ObjectListFilter(
        String status,
        UUID managerId,
        UUID regionId,
        String objectType,
        String q,
        LocalDate createdFrom,
        LocalDate createdTo,
        Sort sort,
        Direction direction,
        int limit) {
    public enum Sort {
        CREATED_AT,
        NAME,
        STATUS,
        INVESTMENT_COMMITTEE_DATE,
        CONTRACT_SIGNED_DATE
    }

    public enum Direction {
        ASC,
        DESC
    }
}
