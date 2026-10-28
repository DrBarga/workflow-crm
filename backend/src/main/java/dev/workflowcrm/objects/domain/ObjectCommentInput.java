package dev.workflowcrm.objects.domain;

import java.util.List;

public record ObjectCommentInput(String text, List<ObjectComment.Mention> mentions) {
    public static final int MAX_MENTIONS = ObjectCommentText.LIMIT / 2;

    public ObjectCommentInput {
        mentions = List.copyOf(mentions);
    }
}
