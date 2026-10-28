package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import dev.workflowcrm.users.api.RegionBoundaryQuery;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ObjectCreationRegion {
    private final ObjectAssignmentQuery users;
    private final RegionBoundaryQuery boundaries;

    ObjectCreationRegion(ObjectAssignmentQuery users, RegionBoundaryQuery boundaries) {
        this.users = users;
        this.boundaries = boundaries;
    }

    UUID resolve(CreateObjectCommand request) {
        var region = request.regionId();
        if (region == null)
            region = boundaries
                    .locate(
                            request.entrance().getLongitude(),
                            request.entrance().getLatitude())
                    .orElseThrow(() -> new UnprocessableInputException(
                            "regionId",
                            "Choose a region manually: no unique loaded boundary matches these coordinates"));
        if (!users.regionExists(region)) throw new UnprocessableInputException("regionId", "Region does not exist");
        return region;
    }
}
