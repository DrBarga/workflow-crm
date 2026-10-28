package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectStatusRepository extends JpaRepository<ObjectStatus, UUID> {
    Optional<ObjectStatus> findByCode(String code);
}
