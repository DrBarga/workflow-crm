package dev.workflowcrm.files.domain;

import dev.workflowcrm.files.api.ObjectFile;
import java.util.List;

public record ObjectFilePage(List<ObjectFile> items, String nextCursor, boolean hasMore) {}
