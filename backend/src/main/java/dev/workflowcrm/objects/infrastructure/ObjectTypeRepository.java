package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectTypeRepository extends JpaRepository<ObjectType, UUID> {
    Optional<ObjectType> findByCode(String code);
}
