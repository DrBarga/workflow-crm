package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public interface ObjectCommentStore {
    record Row(long sequence, ObjectComment comment) {}

    long snapshot(UUID objectId);

    List<Row> page(UUID objectId, long after, long snapshot, int limit);

    ObjectComment get(UUID objectId, UUID commentId);

    ObjectComment create(UUID objectId, UUID actor, String text, List<ObjectComment.Mention> mentions);

    ObjectComment edit(UUID objectId, UUID commentId, String text, List<ObjectComment.Mention> mentions);

    void delete(UUID objectId, UUID commentId);
}
