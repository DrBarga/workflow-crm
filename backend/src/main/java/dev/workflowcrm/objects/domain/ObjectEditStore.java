package dev.workflowcrm.objects.domain;

import java.util.Map;
import java.util.UUID;

public interface ObjectEditStore {
    ObjectType type(String code);

    ObjectDataSource dataSource(UUID id);

    UUID contactId(UUID objectId);

    Map<String, String> snapshot(RealEstateObject object);

    void history(UUID actor, RealEstateObject object, Map<String, String> before, Map<String, String> after);
}
