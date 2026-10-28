package dev.workflowcrm.objects.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ObjectEditValues(
        String name,
        String address,
        String objectType,
        BigDecimal objectArea,
        BigDecimal landArea,
        UUID contactId,
        TechnicalSpecifications technicalSpecifications,
        CommercialTerms commercialTerms,
        String technicalDescription,
        String commercialDescription,
        UUID contractTypeId,
        LocalDate contractSignedDate,
        LocalDate constructionAcceptancePlannedDate,
        LocalDate constructionAcceptanceActualDate,
        LocalDate openingPlannedDate,
        LocalDate openingActualDate,
        LocalDate paymentActPlannedDate,
        LocalDate paymentActActualDate,
        UUID dataSourceId,
        LocalDate preliminaryContractSignedDate,
        LocalDate commissioningDate,
        String shortComment) {
    public ObjectEditValues(
            String name,
            String address,
            String objectType,
            BigDecimal objectArea,
            BigDecimal landArea,
            UUID contactId,
            TechnicalSpecifications technicalSpecifications,
            CommercialTerms commercialTerms,
            String technicalDescription,
            String commercialDescription,
            UUID contractTypeId,
            LocalDate contractSignedDate,
            LocalDate constructionAcceptancePlannedDate,
            LocalDate constructionAcceptanceActualDate,
            LocalDate openingPlannedDate,
            LocalDate openingActualDate,
            LocalDate paymentActPlannedDate,
            LocalDate paymentActActualDate,
            UUID dataSourceId) {
        this(
                name,
                address,
                objectType,
                objectArea,
                landArea,
                contactId,
                technicalSpecifications,
                commercialTerms,
                technicalDescription,
                commercialDescription,
                contractTypeId,
                contractSignedDate,
                constructionAcceptancePlannedDate,
                constructionAcceptanceActualDate,
                openingPlannedDate,
                openingActualDate,
                paymentActPlannedDate,
                paymentActActualDate,
                dataSourceId,
                null,
                null,
                null);
    }
}
