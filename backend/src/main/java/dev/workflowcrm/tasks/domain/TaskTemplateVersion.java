package dev.workflowcrm.tasks.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TaskTemplateVersion(
        UUID id,
        UUID templateId,
        int revision,
        UUID triggerStatusId,
        UUID contractTypeId,
        Instant createdAt,
        List<TemplateItem> items) {
    public TaskTemplateVersion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(templateId);
        Objects.requireNonNull(triggerStatusId);
        Objects.requireNonNull(createdAt);
        if (revision < 1) throw new IllegalArgumentException("Template revision must be positive");
        items = List.copyOf(items);
        if (items.stream().map(TemplateItem::code).distinct().count() != items.size())
            throw new IllegalArgumentException("Template element codes must be unique within a version");
        TemplateDependencies.validate(items);
    }
}
