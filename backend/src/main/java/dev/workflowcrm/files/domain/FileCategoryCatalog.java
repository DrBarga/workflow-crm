package dev.workflowcrm.files.domain;

import java.util.List;
import java.util.UUID;

public interface FileCategoryCatalog {
    record Option(UUID id, String code, String name, boolean isRestricted) {}

    List<Option> active(boolean restricted);
}
