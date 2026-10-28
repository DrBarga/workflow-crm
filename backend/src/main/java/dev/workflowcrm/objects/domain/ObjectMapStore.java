package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ObjectMapStore {
    record Row(
            UUID id,
            UUID managerId,
            UUID regionId,
            String name,
            CreatedObjectView.Coordinates entrance,
            String status) {}

    List<Row> find(ObjectListFilter filter, ObjectListScope scope, ObjectMapBounds bounds, Instant snapshot, int limit);
}
