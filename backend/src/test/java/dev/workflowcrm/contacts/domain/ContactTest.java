package dev.workflowcrm.contacts.domain;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.contacts.api.ContactDetails;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactTest {
    private final ContactDetails details = new ContactDetails("Owner", "Broker", "+380671234567", null);

    @Test
    void requiresAnAuthorForEveryNewContact() {
        assertThatThrownBy(() -> new Contact(details, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsOversizedCommentWithoutChangingMetadata() {
        var contact = new Contact(details, UUID.randomUUID());
        UUID type = UUID.randomUUID();
        contact.describe(type, "Original");
        assertThatThrownBy(() -> contact.describe(null, "a".repeat(2001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(contact.getComment()).isEqualTo("Original");
        assertThat(contact.getContactTypeId()).isEqualTo(type);
    }
}
