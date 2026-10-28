package dev.workflowcrm.contacts.domain;

import java.util.UUID;

public record ContactDuplicate(UUID id, String name, String role) {}
