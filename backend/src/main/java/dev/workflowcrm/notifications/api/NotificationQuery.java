package dev.workflowcrm.notifications.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationQuery {
    record Notification(UUID id, String type, UUID objectId, UUID taskId, Instant createdAt) {
        @com.fasterxml.jackson.annotation.JsonProperty
        public String objectUrl() {
            return objectId == null ? null : "/api/objects/" + objectId;
        }
    }

    List<Notification> latest(UUID recipientId);
}
