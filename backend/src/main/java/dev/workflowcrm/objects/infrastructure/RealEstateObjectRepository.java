package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.RealEstateObject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealEstateObjectRepository extends JpaRepository<RealEstateObject, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {
                "status",
                "type",
                "statusBeforePause",
                "statusBeforeRefusal",
                "refusalReason",
                "specifications.contractTypeReference"
            })
    @org.springframework.data.jpa.repository.Query("select o from RealEstateObject o where o.id in :ids")
    java.util.List<RealEstateObject> findForList(java.util.List<UUID> ids);
}
