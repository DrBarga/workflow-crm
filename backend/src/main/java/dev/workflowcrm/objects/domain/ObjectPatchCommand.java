package dev.workflowcrm.objects.domain;

import java.util.Set;

public record ObjectPatchCommand(long version, Set<String> fields, ObjectEditValues values, String confirmationToken) {
    public ObjectPatchCommand {
        fields = Set.copyOf(fields);
    }

    <T> T value(String field, T supplied, T previous) {
        return fields.contains(field) ? supplied : previous;
    }
}
