package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public record ContactObject(
        UUID id, String name, UUID managerId, UUID regionId, String status, boolean finalStatus, long version) {}
