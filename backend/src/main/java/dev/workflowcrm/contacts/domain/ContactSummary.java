package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public record ContactSummary(
        UUID id, String name, String role, String phone, String email, UUID contactTypeId, long version) {}
