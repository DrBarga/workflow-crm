package dev.workflowcrm.objects.domain;

import dev.workflowcrm.notifications.api.NotificationQueue;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class ObjectCommentMentions {
    private final ObjectCommentAccess access;
    private final NotificationQueue notifications;

    ObjectCommentMentions(ObjectCommentAccess access, NotificationQueue notifications) {
        this.access = access;
        this.notifications = notifications;
    }

    List<ObjectComment.Mention> validate(
            ObjectCardStore.Summary object, String text, List<ObjectComment.Mention> mentions) {
        if (mentions.size() > ObjectCommentInput.MAX_MENTIONS)
            throw invalid("Mention count exceeds the comment text limit");
        var sorted = mentions.stream()
                .sorted(Comparator.comparingInt(ObjectComment.Mention::offset))
                .toList();
        int end = 0;
        var recipients = new HashMap<UUID, ObjectAssignmentQuery.Member>();
        for (var mention : sorted) {
            int offset = mention.offset();
            int length = mention.length();
            if (mention.userId() == null
                    || offset < end
                    || offset < 0
                    || offset > text.length()
                    || length < 2
                    || length > text.length() - offset)
                throw invalid("Mention must identify a non-overlapping @name in the comment text");
            var user = recipients.computeIfAbsent(mention.userId(), id -> recipient(id, object));
            if (!text.substring(offset, offset + length).equals("@" + user.fullName()))
                throw invalid("Mention text must match the selected user's current name");
            end = offset + length;
        }
        return sorted;
    }

    private ObjectAssignmentQuery.Member recipient(UUID userId, ObjectCardStore.Summary object) {
        try {
            var user = access.reader(userId);
            access.requireObject(user, object);
            return user;
        } catch (AccessDeniedException denied) {
            throw invalid("Mentioned user must be active and able to read this object's comments");
        }
    }

    void notifyNew(UUID actor, UUID object, ObjectComment previous, ObjectComment current) {
        var existing = new HashSet<UUID>();
        if (previous != null) previous.mentions().forEach(m -> existing.add(m.userId()));
        var recipients = new LinkedHashSet<UUID>();
        current.mentions().forEach(m -> recipients.add(m.userId()));
        recipients.remove(actor);
        recipients.removeAll(existing);
        String key = "comment-mention:" + current.id() + ":" + current.version();
        for (UUID recipient : recipients) notifications.enqueue(recipient, key, "COMMENT_MENTIONED", object, null);
    }

    private UnprocessableInputException invalid(String message) {
        return new UnprocessableInputException("mentions", message);
    }
}
