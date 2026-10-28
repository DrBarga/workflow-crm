package dev.workflowcrm.objects.domain;

import java.util.List;

public record ObjectMapResult(List<ObjectMapPin> content, int limit, boolean truncated) {
    public ObjectMapResult {
        content = List.copyOf(content);
    }
}
