package dev.workflowcrm.files.domain;

import java.time.Instant;

public record ObjectFileLink(String url, Instant expiresAt) {}
