package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String role;

    @Column(length = 64)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(length = 2000)
    private String comment;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(name = "contact_type_id")
    private UUID contactTypeId;

    @Column(name = "normalized_phone", insertable = false, updatable = false, length = 64)
    private String normalizedPhone;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Contact() {}

    public Contact(ContactDetails details, UUID authorId) {
        this.authorId = Objects.requireNonNull(authorId, "Contact author is required");
        replace(details, null, null);
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getContactTypeId() {
        return contactTypeId;
    }

    public String getComment() {
        return comment;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public void describe(UUID contactTypeId, String comment) {
        String validatedComment = ContactFields.optional(comment, 2000);
        this.contactTypeId = contactTypeId;
        this.comment = validatedComment;
    }

    public ContactDetails details() {
        return new ContactDetails(name, role, phone, email);
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    void replace(ContactDetails details, UUID typeId, String comment) {
        var fields = new ContactFields(details, typeId, comment);
        name = fields.name();
        role = fields.role();
        phone = fields.phone();
        email = fields.email();
        contactTypeId = fields.typeId();
        this.comment = fields.comment();
    }

    void delete(Instant at) {
        deletedAt = Objects.requireNonNull(at);
    }

    void requireVersion(long expected) {
        if (getVersion() != expected) throw new ContactConflictException(getVersion(), null);
    }
}
