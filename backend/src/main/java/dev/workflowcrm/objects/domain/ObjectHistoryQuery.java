package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public interface ObjectHistoryQuery {
    List<ObjectHistoryPage.Entry> page(UUID object, long after, int limit);
}
