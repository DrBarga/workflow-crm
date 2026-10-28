package dev.workflowcrm.contacts.api;

import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

record ContactWriteRequest(
        @NotBlank(groups = Create.class) @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
        @NotBlank(groups = Create.class) @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String role,
        @Size(max = 64) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 2000) @Pattern(regexp = "[^\\x00]*") String comment,
        UUID contactTypeId,
        @Size(max = 100) List<@NotNull UUID> objectIds,
        @NotNull(groups = Patch.class) @PositiveOrZero Long version) {
    interface Create extends Default {}

    interface Patch extends Default {}

    ContactWriteRequest {
        name = text(name);
        role = text(role);
        phone = text(phone);
        email = text(email);
        if (email != null) email = email.toLowerCase(Locale.ROOT);
        comment = text(comment);
        objectIds = objectIds == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(objectIds));
    }

    ContactDetails details(String normalizedPhone) {
        return new ContactDetails(name, role, normalizedPhone, email);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
