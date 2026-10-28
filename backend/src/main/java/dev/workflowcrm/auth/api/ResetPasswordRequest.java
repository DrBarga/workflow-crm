package dev.workflowcrm.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token, @NotBlank @Size(min = 12, max = 128) String password) {}
