package dev.workflowcrm.objects.domain;

import java.util.UUID;

public interface ObjectDeletionAvailability {
    boolean pending(UUID objectId);
}
