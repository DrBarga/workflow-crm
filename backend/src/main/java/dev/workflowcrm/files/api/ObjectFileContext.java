package dev.workflowcrm.files.api;

import java.util.UUID;

/** Object ownership and access evaluated by the owning module. */
public interface ObjectFileContext {
    record Access(String role, boolean fullView, boolean responsibleManager, long version) {}

    Access read(UUID actor, UUID objectId);

    Access lock(UUID actor, UUID objectId);

    void uploaded(UUID actor, UUID objectId, long version, ObjectFile file);

    void deleted(UUID actor, UUID objectId, long version, ObjectFile file);
}
