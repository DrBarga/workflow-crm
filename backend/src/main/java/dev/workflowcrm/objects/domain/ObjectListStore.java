package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ObjectListStore {
    record Row(UUID id, String value, UUID managerId, UUID regionId, ObjectListItem restricted) {}

    List<Row> find(ObjectListFilter filter, ObjectListScope scope, ObjectListPosition position);

    Map<UUID, ObjectListItem.Full> full(List<UUID> ids);
}
