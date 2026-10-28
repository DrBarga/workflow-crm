package dev.workflowcrm.objects.domain;

import java.util.UUID;

public record CreateObjectCommand(
        String name,
        String objectType,
        String address,
        UUID regionId,
        EntranceCoordinates entrance,
        PropertyAreas areas,
        ObjectContact contact,
        UUID managerId,
        UUID contactId,
        TechnicalSpecifications technicalSpecifications,
        CommercialTerms commercialTerms,
        UUID dataSourceId,
        String shortComment) {
    public CreateObjectCommand(
            String name,
            String objectType,
            String address,
            UUID regionId,
            EntranceCoordinates entrance,
            PropertyAreas areas,
            ObjectContact contact,
            UUID managerId,
            UUID contactId,
            TechnicalSpecifications technicalSpecifications,
            CommercialTerms commercialTerms,
            UUID dataSourceId) {
        this(
                name,
                objectType,
                address,
                regionId,
                entrance,
                areas,
                contact,
                managerId,
                contactId,
                technicalSpecifications,
                commercialTerms,
                dataSourceId,
                null);
    }

    public CreateObjectCommand(
            String name,
            String objectType,
            String address,
            UUID regionId,
            EntranceCoordinates entrance,
            PropertyAreas areas,
            ObjectContact contact,
            UUID managerId,
            UUID contactId,
            TechnicalSpecifications technicalSpecifications,
            CommercialTerms commercialTerms) {
        this(
                name,
                objectType,
                address,
                regionId,
                entrance,
                areas,
                contact,
                managerId,
                contactId,
                technicalSpecifications,
                commercialTerms,
                null);
    }
}
