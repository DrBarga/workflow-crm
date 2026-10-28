package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "object_history")
public class ObjectHistory extends BaseEntity {
    public enum ActorType {
        USER,
        SYSTEM
    }

    @Column(name = "object_id", nullable = false, updatable = false)
    private UUID objectId;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 16)
    private ActorType actorType;

    @Column(name = "field_name", nullable = false, updatable = false, length = 100)
    private String fieldName;

    @Column(name = "previous_value", updatable = false, columnDefinition = "text")
    private String previousValue;

    @Column(name = "new_value", updatable = false, columnDefinition = "text")
    private String newValue;

    @Column(name = "object_version", nullable = false, updatable = false)
    private long objectVersion;

    protected ObjectHistory() {}

    public ObjectHistory(
            UUID objectId,
            UUID authorId,
            ActorType actorType,
            String fieldName,
            String previousValue,
            String newValue,
            long objectVersion) {
        this.objectId = Objects.requireNonNull(objectId);
        this.authorId = authorId;
        this.actorType = Objects.requireNonNull(actorType);
        if ((actorType == ActorType.USER) != (authorId != null)) {
            throw new IllegalArgumentException("A user history entry requires an author; a system entry has no author");
        }
        if (objectVersion < 0) throw new IllegalArgumentException("Object version must not be negative");
        this.fieldName = ObjectText.required(fieldName, 100, "Field name");
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.objectVersion = objectVersion;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public long getObjectVersion() {
        return objectVersion;
    }
}
