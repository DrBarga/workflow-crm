package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ObjectStatusStore {
    RealEstateObject lock(UUID id);

    ObjectStatus status(String code);

    List<ObjectStatusTransition> transitions(UUID fromStatusId, String target, String milestoneField);

    RefusalReason refusalReason(UUID id);

    List<RefusalReason> manualRefusalReasons();

    ContractType contractType(UUID id);

    void save(RealEstateObject object);

    void history(
            UUID actorId, RealEstateObject object, String type, Map<String, String> before, Map<String, String> after);
}
