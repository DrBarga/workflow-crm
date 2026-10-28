package dev.workflowcrm.objects.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreatedObjectView(
        UUID id,
        String name,
        String objectType,
        String address,
        UUID regionId,
        UUID managerId,
        String status,
        String statusName,
        Coordinates entrance,
        BigDecimal objectArea,
        BigDecimal landArea,
        Contact contact,
        UUID contactId,
        UUID authorId,
        Instant createdAt,
        long version,
        String googleMapsUrl,
        TechnicalSpecifications technicalSpecifications,
        CommercialTerms commercialTerms,
        UUID dataSourceId,
        String shortComment) {
    public record Coordinates(BigDecimal longitude, BigDecimal latitude) {}

    public record Contact(String name, String role, String phone, String email) {}

    public static CreatedObjectView of(RealEstateObject object, UUID contactId) {
        return of(object, contactId, false);
    }

    public static CreatedObjectView forList(RealEstateObject object, UUID contactId) {
        return of(object, contactId, true);
    }

    private static CreatedObjectView of(RealEstateObject object, UUID contactId, boolean maskPhone) {
        var contact = object.getContact();
        var specifications = object.getSpecifications();
        return new CreatedObjectView(
                object.getId(),
                object.getName(),
                object.getType() == null ? null : object.getType().getCode(),
                object.getAddress(),
                object.getRegionId(),
                object.getManagerId(),
                object.getStatus().getCode(),
                object.getStatus().getName(),
                new Coordinates(
                        object.getEntrance().getLongitude(),
                        object.getEntrance().getLatitude()),
                object.getAreas().getObjectArea(),
                object.getAreas().getLandArea(),
                contact == null
                        ? null
                        : new Contact(
                                contact.getName(),
                                contact.getRole(),
                                maskPhone && contact.getPhone() != null ? "***" : contact.getPhone(),
                                contact.getEmail()),
                contactId,
                object.getAuthorId(),
                object.getCreatedAt(),
                object.getVersion(),
                object.getGoogleMapsUrl(),
                specifications == null ? null : specifications.getTechnical(),
                specifications == null ? null : specifications.getCommercial(),
                object.getDataSource() == null ? null : object.getDataSource().getId(),
                object.getShortComment());
    }
}
