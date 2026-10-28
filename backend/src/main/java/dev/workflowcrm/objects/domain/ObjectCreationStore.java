package dev.workflowcrm.objects.domain;

import java.util.Optional;
import java.util.UUID;

public interface ObjectCreationStore {
    void lockRequest(UUID actor, String key);

    void lockCreation();

    record PreviousRequest(String hash, RealEstateObject object, UUID contactId) {}

    Optional<PreviousRequest> previousRequest(UUID actor, String key);

    Optional<ObjectType> type(String code);

    Optional<ObjectDataSource> dataSource(UUID id);

    RealEstateObject save(ObjectRegistration registration);

    RealEstateObject save(ObjectRegistration registration, ObjectMilestones dates);

    void recordCreation(UUID actor, String key, String hash, RealEstateObject object, UUID contactId);

    void recordDuplicateConfirmation(UUID actor, RealEstateObject object, long matchCount);
}
