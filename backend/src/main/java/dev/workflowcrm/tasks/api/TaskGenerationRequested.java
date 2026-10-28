package dev.workflowcrm.tasks.api;

import java.util.UUID;

public record TaskGenerationRequested(ObjectTaskTransition.Context context, TaskTransitionPlan plan, UUID actorId) {}
