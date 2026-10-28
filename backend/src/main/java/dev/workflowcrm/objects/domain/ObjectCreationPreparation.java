package dev.workflowcrm.objects.domain;

import dev.workflowcrm.integrations.api.ReverseGeocoder;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectCreationPreparation {
    private final ObjectCreationStore store;
    private final ObjectCreationAccess access;
    private final ObjectAssignmentQuery users;
    private final ReverseGeocoder geocoder;
    private final UkraineBoundary boundary;
    private final ObjectCreationRegion regions;

    ObjectCreationPreparation(
            ObjectCreationStore store,
            ObjectCreationAccess access,
            ObjectAssignmentQuery users,
            ReverseGeocoder geocoder,
            UkraineBoundary boundary,
            ObjectCreationRegion regions) {
        this.store = store;
        this.access = access;
        this.users = users;
        this.geocoder = geocoder;
        this.boundary = boundary;
        this.regions = regions;
    }

    ObjectRegistration prepare(ObjectAssignmentQuery.Member actor, CreateObjectCommand request) {
        return prepare(actor.id(), actor, request);
    }

    ObjectRegistration prepareImport(UUID actor, CreateObjectCommand request) {
        return prepare(actor, null, request);
    }

    void authorize(ObjectAssignmentQuery.Member actor, CreateObjectCommand request) {
        access.manager(actor, request.managerId(), regions.resolve(request));
    }

    private ObjectRegistration prepare(UUID author, ObjectAssignmentQuery.Member actor, CreateObjectCommand request) {
        if (!boundary.contains(request.entrance()))
            throw new UnprocessableInputException("entrance", "Entrance coordinates must be within Ukraine");
        UUID region = actor == null && request.regionId() == null ? null : regions.resolve(request);
        UUID manager = region == null
                ? null
                : actor == null
                        ? importManager(request.managerId(), region)
                        : access.manager(actor, request.managerId(), region);
        var type = request.objectType() == null
                ? null
                : store.type(request.objectType())
                        .orElseThrow(
                                () -> new UnprocessableInputException("objectType", "Choose an active object type"));
        var location = request.address() == null || actor == null && (request.name() == null || region == null)
                ? geocoder.resolve(
                        request.entrance().getLongitude(), request.entrance().getLatitude())
                : null;
        if (region == null) {
            region = users.regionByGeocoding(location.regionCode(), location.regionName())
                    .orElseThrow(() -> new UnprocessableInputException(
                            "regionId", "No CRM region matches these coordinates; choose a region manually"));
            manager = importManager(request.managerId(), region);
        }
        String address = request.address() == null ? location.address() : request.address();
        String name = ObjectCreationName.resolve(
                request.name(), actor == null && request.name() == null ? location.address() : address);
        var dataSource = request.dataSourceId() == null
                ? null
                : store.dataSource(request.dataSourceId())
                        .orElseThrow(
                                () -> new UnprocessableInputException("dataSourceId", "Choose an active data source"));
        return new ObjectRegistration(
                name,
                type,
                address,
                region,
                manager,
                author,
                request.entrance(),
                request.areas(),
                request.contact(),
                request.technicalSpecifications(),
                request.commercialTerms(),
                dataSource,
                request.shortComment());
    }

    private UUID importManager(UUID requested, UUID region) {
        if (requested == null)
            throw new UnprocessableInputException("managerId", "Choose an active manager of the object region");
        var manager = users.member(requested)
                .orElseThrow(() -> new UnprocessableInputException("managerId", "Manager does not exist"));
        if (!manager.active() || !"MANAGER".equals(manager.role()) || !manager.belongsTo(region))
            throw new UnprocessableInputException("managerId", "Choose an active manager of the object region");
        return manager.id();
    }
}
