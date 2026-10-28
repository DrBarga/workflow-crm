package dev.workflowcrm.contacts.domain;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.contacts.api.ContactDetails;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactConfirmationTest {
    @Test
    void confirmationIsBoundToActorAndCompleteLinkedObjectState() {
        var actor = UUID.randomUUID();
        var contact = new Contact(new ContactDetails("Name", "Role", null, "a@example.com"), actor);
        var object = new ContactObject(UUID.randomUUID(), "Name", actor, UUID.randomUUID(), "FOUND", false, 0);
        var token = ContactDeletionConfirmation.token(actor, contact, List.of(object));
        assertThat(ContactDeletionConfirmation.token(UUID.randomUUID(), contact, List.of(object)))
                .isNotEqualTo(token);
        assertThat(ContactDeletionConfirmation.token(actor, contact, List.of())).isNotEqualTo(token);
        var changed = new ContactObject(object.id(), object.name(), actor, object.regionId(), "OPENED", true, 1);
        assertThat(ContactDeletionConfirmation.token(actor, contact, List.of(changed)))
                .isNotEqualTo(token);
    }
}
