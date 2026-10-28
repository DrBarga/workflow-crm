package dev.workflowcrm.tasks.api;

import java.util.UUID;

public interface TaskObjectContext {
    record State(UUID id, UUID regionId, String status, boolean terminal) {
        public boolean paused() {
            return "PAUSED".equals(status);
        }
    }

    State lock(UUID objectId);
}
