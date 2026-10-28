package dev.workflowcrm.tasks.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TemplateItem(
        UUID id,
        String name,
        DeadlineRule deadline,
        AssignmentRule assignment,
        int sortOrder,
        Set<UUID> predecessors,
        boolean needsReview,
        UUID resultRecipientRoleId,
        Integer escalationDays,
        String code) {
    public TemplateItem(
            UUID id,
            String name,
            DeadlineRule deadline,
            AssignmentRule assignment,
            int sortOrder,
            Set<UUID> predecessors,
            boolean needsReview,
            UUID resultRecipientRoleId,
            Integer escalationDays) {
        this(
                id,
                name,
                deadline,
                assignment,
                sortOrder,
                predecessors,
                needsReview,
                resultRecipientRoleId,
                escalationDays,
                "ITEM_" + id.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT));
    }

    public TemplateItem(
            UUID id,
            String name,
            DeadlineRule deadline,
            AssignmentRule assignment,
            int sortOrder,
            Set<UUID> predecessors) {
        this(id, name, deadline, assignment, sortOrder, predecessors, true, null, null);
    }

    public TemplateItem {
        Objects.requireNonNull(id);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,99}"))
            throw new IllegalArgumentException("Invalid item code");
        Objects.requireNonNull(deadline);
        Objects.requireNonNull(assignment);
        if (name == null || name.isBlank() || name.length() > 250)
            throw new IllegalArgumentException("Invalid item name");
        if (sortOrder < 0) throw new IllegalArgumentException("Negative item sort order");
        if (escalationDays != null && (escalationDays < 0 || escalationDays > 3650))
            throw new IllegalArgumentException("Invalid escalation interval");
        predecessors = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(predecessors));
        if (predecessors.contains(id)) throw new IllegalArgumentException("An item cannot depend on itself");
    }
}
