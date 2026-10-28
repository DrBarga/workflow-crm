package dev.workflowcrm.tasks.api;

import java.util.UUID;

public record TaskSetCreated(UUID objectId, UUID actorId, UUID taskSetId, UUID templateId, int taskCount) {}
