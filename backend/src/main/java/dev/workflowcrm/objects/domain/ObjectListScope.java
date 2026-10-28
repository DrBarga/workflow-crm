package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public record ObjectListScope(UUID actorId, String role, List<UUID> regionIds) {
    public ObjectListScope {
        regionIds = regionIds.stream().distinct().sorted().toList();
    }

    public boolean regional() {
        return role.equals("MANAGER") || role.equals("REGIONAL_MANAGER");
    }

    public boolean contract() {
        return role.equals("CONTRACT_MANAGER");
    }

    public boolean regionRestricted() {
        return role.equals("MANAGER");
    }

    public boolean full(UUID managerId, UUID regionId) {
        return switch (role) {
            case "MANAGER" -> actorId.equals(managerId);
            case "REGIONAL_MANAGER" -> regionIds.contains(regionId);
            case "CONTRACT_MANAGER" -> false;
            case "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN" -> true;
            default -> false;
        };
    }
}
