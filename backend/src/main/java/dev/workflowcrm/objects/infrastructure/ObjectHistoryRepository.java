package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ObjectHistoryRepository extends Repository<ObjectHistory, UUID> {
    ObjectHistory save(ObjectHistory history);

    List<ObjectHistory> findByObjectIdOrderByCreatedAtAsc(UUID objectId);
}
