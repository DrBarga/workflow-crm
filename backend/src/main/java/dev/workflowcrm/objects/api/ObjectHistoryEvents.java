package dev.workflowcrm.objects.api;

import java.util.UUID;

/** Records module events in the same transaction as the object operation. */
public interface ObjectHistoryEvents {
    enum Type {
        FILE_UPLOADED,
        FILE_DELETED,
        TASK_DEADLINES_SHIFTED,
        TASK_STATUS_CHANGED,
        TASK_SET_CREATED,
        COMMENT_CREATED,
        COMMENT_EDITED,
        COMMENT_DELETED
    }

    void record(UUID objectId, UUID actorId, long objectVersion, Type type, Object previousValue, Object newValue);

    /** Locks the object and records a related operation against its current persisted version. */
    void record(UUID objectId, UUID actorId, Type type, Object previousValue, Object newValue);
}
