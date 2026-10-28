package dev.workflowcrm.notifications.api;

import java.util.UUID;

public interface NotificationQueue {
    void enqueue(UUID recipientId, String key, String type, UUID objectId, UUID taskId);

    void administrators(String key, String type, UUID objectId);
}
