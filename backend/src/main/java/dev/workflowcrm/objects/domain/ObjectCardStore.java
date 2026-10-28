package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public interface ObjectCardStore {
    record Summary(UUID id, String name, CreatedObjectView.Coordinates entrance, UUID managerId, UUID regionId) {}

    Summary summary(UUID id);

    RealEstateObject full(UUID id);

    UUID contactId(UUID id);

    List<ObjectStatusTransition> transitions(UUID statusId);

    int preparationOrder();
}
