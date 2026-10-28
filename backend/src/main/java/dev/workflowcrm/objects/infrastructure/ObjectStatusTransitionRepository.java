package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectStatusTransition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectStatusTransitionRepository extends JpaRepository<ObjectStatusTransition, UUID> {
    Optional<ObjectStatusTransition> findByCode(String code);

    List<ObjectStatusTransition> findByFromStatusIdAndActiveTrue(UUID fromStatusId);
}
