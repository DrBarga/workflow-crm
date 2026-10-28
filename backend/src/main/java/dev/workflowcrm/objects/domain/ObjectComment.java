package dev.workflowcrm.objects.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ObjectComment(
        UUID id,
        Author author,
        String text,
        Instant createdAt,
        Instant editedAt,
        boolean edited,
        String editLabel,
        long version,
        List<Mention> mentions) {
    public ObjectComment {
        mentions = List.copyOf(mentions);
    }

    public record Author(UUID id, String name) {}

    public record Mention(UUID userId, int offset, int length) {}
}
