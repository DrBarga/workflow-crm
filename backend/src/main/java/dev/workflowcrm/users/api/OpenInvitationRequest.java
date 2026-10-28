package dev.workflowcrm.users.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenInvitationRequest(@NotBlank @Size(max = 128) String token) {}
