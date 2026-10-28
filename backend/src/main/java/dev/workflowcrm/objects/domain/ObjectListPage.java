package dev.workflowcrm.objects.domain;

import java.util.List;

public record ObjectListPage(List<ObjectListItem> content, String nextCursor, boolean hasMore) {
    public ObjectListPage {
        content = List.copyOf(content);
    }
}
