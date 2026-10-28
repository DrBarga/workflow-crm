package dev.workflowcrm.users.domain;

import java.util.List;
import java.util.UUID;

public interface UserRegionCatalog {
    record Option(UUID id, String code, String name) {}

    List<Option> regions(List<UUID> ids, boolean network);
}
