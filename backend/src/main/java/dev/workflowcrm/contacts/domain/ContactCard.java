package dev.workflowcrm.contacts.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContactCard(
        UUID id,
        String name,
        String role,
        String phone,
        String email,
        String comment,
        UUID contactTypeId,
        UUID authorId,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<LinkedObject> objects,
        boolean canEdit,
        boolean canDelete) {
    public record LinkedObject(UUID id, String name, String status) {}

    public ContactCard {
        objects = List.copyOf(objects);
    }
}
