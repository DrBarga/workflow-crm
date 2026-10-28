package dev.workflowcrm.contacts.domain;

import java.util.List;
import java.util.UUID;

public record ContactScope(UUID actorId, String role, List<UUID> regions) {
    public ContactScope {
        regions = List.copyOf(regions);
    }

    public boolean network() {
        return List.of("DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN").contains(role);
    }

    public boolean writer() {
        return List.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN")
                .contains(role);
    }

    public boolean sees(ContactObject object) {
        return switch (role) {
            case "MANAGER" -> actorId.equals(object.managerId()) && regions.contains(object.regionId());
            case "REGIONAL_MANAGER" -> regions.contains(object.regionId());
            case "CONTRACT_MANAGER" ->
                List.of("CONTRACT_APPROVAL", "CONTRACT_SIGNED").contains(object.status());
            default -> network();
        };
    }

    public boolean sees(Contact contact, List<ContactObject> objects) {
        return network()
                || objects.stream().anyMatch(this::sees)
                || (writer() && actorId.equals(contact.getAuthorId()) && objects.isEmpty());
    }
}
