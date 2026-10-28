package dev.workflowcrm.users.api;

import dev.workflowcrm.users.domain.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String fullName,
        @NotNull RoleCode role,
        UUID regionId,
        java.util.List<UUID> regionIds) {
    public CreateUserRequest {
        email = email == null ? null : email.strip();
        fullName = fullName == null ? null : fullName.strip();
        regionIds = UserRegionInput.resolve(regionId, regionIds);
    }

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown user creation field");
    }
}
