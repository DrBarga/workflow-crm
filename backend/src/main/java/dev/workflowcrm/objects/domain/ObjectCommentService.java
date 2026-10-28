package dev.workflowcrm.objects.domain;

import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectCommentService {
    private final ObjectCommentAccess access;
    private final ObjectCardStore objects;
    private final ObjectStatusStore locks;
    private final ObjectDeletionAvailability deletions;
    private final ObjectCommentStore comments;
    private final ObjectHistoryEvents history;
    private final ObjectCommentMentions mentions;

    ObjectCommentService(
            ObjectCommentAccess access,
            ObjectCardStore objects,
            ObjectStatusStore locks,
            ObjectDeletionAvailability deletions,
            ObjectCommentStore comments,
            ObjectHistoryEvents history,
            ObjectCommentMentions mentions) {
        this.access = access;
        this.objects = objects;
        this.locks = locks;
        this.deletions = deletions;
        this.comments = comments;
        this.history = history;
        this.mentions = mentions;
    }

    @Transactional(readOnly = true)
    public void requireWriter(UUID actor, UUID object) {
        var user = access.writer(actor);
        available(object);
        access.requireObject(user, objects.summary(object));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectCommentPage get(UUID actor, UUID object, String cursor, int limit) {
        var user = access.reader(actor);
        available(object);
        access.requireObject(user, objects.summary(object));
        if (limit < 1 || limit > 100)
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Comment limit must be between 1 and 100");
        var position = cursor == null
                ? new ObjectCommentCursor.Position(0, comments.snapshot(object))
                : ObjectCommentCursor.decode(object, cursor);
        var rows = comments.page(object, position.after(), position.snapshot(), limit + 1);
        boolean more = rows.size() > limit;
        var visible = rows.stream().limit(limit).toList();
        String next =
                more ? ObjectCommentCursor.encode(object, visible.getLast().sequence(), position.snapshot()) : null;
        return new ObjectCommentPage(
                visible.stream().map(ObjectCommentStore.Row::comment).toList(), next, more);
    }

    @Transactional
    public ObjectComment create(UUID actor, UUID object, String text) {
        return create(actor, object, new ObjectCommentInput(text, java.util.List.of()));
    }

    @Transactional
    public ObjectComment create(UUID actor, UUID object, ObjectCommentInput input) {
        var locked = write(actor, object);
        String text = ObjectCommentText.require(input.text());
        var selected = mentions.validate(objects.summary(object), text, input.mentions());
        var created = comments.create(object, actor, text, selected);
        history.record(object, actor, locked.getVersion(), ObjectHistoryEvents.Type.COMMENT_CREATED, null, created);
        mentions.notifyNew(actor, object, null, created);
        return created;
    }

    @Transactional
    public ObjectComment edit(UUID actor, UUID object, UUID id, String text, long version) {
        return edit(actor, object, id, new ObjectCommentInput(text, java.util.List.of()), version);
    }

    @Transactional
    public ObjectComment edit(UUID actor, UUID object, UUID id, ObjectCommentInput input, long version) {
        var locked = write(actor, object);
        var previous = comments.get(object, id);
        access.requireAuthor(actor, previous);
        if (previous.version() != version) throw new ObjectCommentConflictException(previous.version());
        String value = ObjectCommentText.require(input.text());
        var selected = mentions.validate(objects.summary(object), value, input.mentions());
        if (value.equals(previous.text()) && selected.equals(previous.mentions())) return previous;
        var edited = comments.edit(object, id, value, selected);
        history.record(object, actor, locked.getVersion(), ObjectHistoryEvents.Type.COMMENT_EDITED, previous, edited);
        mentions.notifyNew(actor, object, previous, edited);
        return edited;
    }

    @Transactional
    public void delete(UUID actor, UUID object, UUID id) {
        var locked = write(actor, object);
        var previous = comments.get(object, id);
        access.requireAuthor(actor, previous);
        comments.delete(object, id);
        history.record(object, actor, locked.getVersion(), ObjectHistoryEvents.Type.COMMENT_DELETED, previous, null);
    }

    private RealEstateObject write(UUID actor, UUID object) {
        var user = access.lockWriter(actor);
        var locked = locks.lock(object);
        available(object);
        access.requireObject(user, objects.summary(object));
        return locked;
    }

    private void available(UUID object) {
        if (deletions.pending(object))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object deletion is in progress");
    }
}
