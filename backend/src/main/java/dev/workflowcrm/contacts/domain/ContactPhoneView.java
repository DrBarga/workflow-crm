package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contact_phone_views")
@org.hibernate.annotations.Immutable
public class ContactPhoneView extends BaseEntity {
    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "viewer_id", nullable = false, updatable = false)
    private UUID viewerId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    protected ContactPhoneView() {}

    public ContactPhoneView(UUID contactId, UUID viewerId, Instant viewedAt) {
        this.contactId = Objects.requireNonNull(contactId);
        this.viewerId = Objects.requireNonNull(viewerId);
        this.viewedAt = Objects.requireNonNull(viewedAt);
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }
}
