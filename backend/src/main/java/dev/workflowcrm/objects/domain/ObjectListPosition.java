package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.UUID;

public record ObjectListPosition(UUID id, String value, Instant snapshot, Instant expiresAt) {}
