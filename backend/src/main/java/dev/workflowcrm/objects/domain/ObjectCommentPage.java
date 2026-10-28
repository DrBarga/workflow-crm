package dev.workflowcrm.objects.domain;

import java.util.List;

public record ObjectCommentPage(List<ObjectComment> items, String nextCursor, boolean hasMore) {}
