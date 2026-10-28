package dev.workflowcrm.objects.domain;

public class ObjectCommentConflictException extends ObjectEditConflictException {
    public ObjectCommentConflictException(long version) {
        super(version);
    }

    @Override
    public String getMessage() {
        return "Comment has changed; reload before editing";
    }
}
