package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public interface ObjectCatalogStore {
    record Option(UUID id, String code, String name) {}

    List<Option> dataSources();

    List<Option> contractTypes();
}
