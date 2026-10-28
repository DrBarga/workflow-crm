package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.RefusalReason;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefusalReasonRepository extends JpaRepository<RefusalReason, UUID> {
    Optional<RefusalReason> findByCode(String code);
}
