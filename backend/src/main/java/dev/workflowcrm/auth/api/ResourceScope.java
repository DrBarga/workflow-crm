package dev.workflowcrm.auth.api;

import java.util.UUID;

/** Ownership and region loaded by the resource module, never taken from request parameters. */
public record ResourceScope(UUID ownerId, UUID regionId) {}
