package dev.workflowcrm.objects.domain;

import java.util.UUID;

public record ObjectEntryPointResult(
        boolean saved,
        UUID id,
        long version,
        UUID approvalRequestId,
        CreatedObjectView.Coordinates entrance,
        String googleMapsUrl) {
    static ObjectEntryPointResult of(boolean saved, RealEstateObject object, UUID request) {
        return new ObjectEntryPointResult(
                saved,
                object.getId(),
                object.getVersion(),
                request,
                new CreatedObjectView.Coordinates(
                        object.getEntrance().getLongitude(),
                        object.getEntrance().getLatitude()),
                object.getGoogleMapsUrl());
    }
}
