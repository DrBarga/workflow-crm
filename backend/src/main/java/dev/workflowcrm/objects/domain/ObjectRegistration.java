package dev.workflowcrm.objects.domain;

import java.util.UUID;

public record ObjectRegistration(
        String name,
        ObjectType type,
        String address,
        UUID regionId,
        UUID managerId,
        UUID authorId,
        EntranceCoordinates entrance,
        PropertyAreas areas,
        ObjectContact contact,
        TechnicalSpecifications technicalSpecifications,
        CommercialTerms commercialTerms,
        ObjectDataSource dataSource,
        String shortComment) {
    public ObjectRegistration(
            String name,
            ObjectType type,
            String address,
            UUID regionId,
            UUID managerId,
            UUID authorId,
            EntranceCoordinates entrance,
            PropertyAreas areas,
            ObjectContact contact,
            TechnicalSpecifications technicalSpecifications,
            CommercialTerms commercialTerms,
            ObjectDataSource dataSource) {
        this(
                name,
                type,
                address,
                regionId,
                managerId,
                authorId,
                entrance,
                areas,
                contact,
                technicalSpecifications,
                commercialTerms,
                dataSource,
                null);
    }

    public ObjectRegistration(
            String name,
            ObjectType type,
            String address,
            UUID regionId,
            UUID managerId,
            UUID authorId,
            EntranceCoordinates entrance,
            PropertyAreas areas,
            ObjectContact contact,
            TechnicalSpecifications technicalSpecifications,
            CommercialTerms commercialTerms) {
        this(
                name,
                type,
                address,
                regionId,
                managerId,
                authorId,
                entrance,
                areas,
                contact,
                technicalSpecifications,
                commercialTerms,
                null);
    }

    public ObjectRegistration(
            String name,
            ObjectType type,
            String address,
            UUID regionId,
            UUID managerId,
            UUID authorId,
            EntranceCoordinates entrance,
            PropertyAreas areas,
            ObjectContact contact) {
        this(name, type, address, regionId, managerId, authorId, entrance, areas, contact, null, null);
    }
}
