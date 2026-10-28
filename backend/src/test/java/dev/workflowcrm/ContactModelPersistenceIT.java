package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.contacts.api.ContactDetails;
import dev.workflowcrm.contacts.domain.Contact;
import dev.workflowcrm.contacts.domain.ContactPhoneView;
import dev.workflowcrm.contacts.domain.ContactType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ContactModelPersistenceIT extends ObjectApiTestSupport {
    @Autowired
    EntityManager entities;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void persistsMetadataDictionaryAndRepeatedPhoneViewsWithoutChangingExistingApi() {
        transactions.executeWithoutResult(status -> {
            var type = entities.createQuery("select t from ContactType t where t.code='ENGINEER'", ContactType.class)
                    .getSingleResult();
            var contact = new Contact(
                    new ContactDetails("Інженер", "Головний інженер", "+380 (67) 123-45-67", null), manager);
            contact.describe(type.getId(), "  Обслуговування приміщення  ");
            entities.persist(contact);
            entities.flush();
            entities.refresh(contact);
            assertThat(contact.getAuthorId()).isEqualTo(manager);
            assertThat(contact.getContactTypeId()).isEqualTo(type.getId());
            assertThat(contact.getComment()).isEqualTo("Обслуговування приміщення");
            assertThat(contact.getNormalizedPhone()).isEqualTo("380671234567");
            assertThat(contact.getCreatedAt()).isNotNull();
            assertThat(type.isActive()).isTrue();
            assertThat(type.getName()).isEqualTo("Інженер");
            var now = Instant.now();
            var view = new ContactPhoneView(contact.getId(), manager, now);
            entities.persist(view);
            entities.persist(new ContactPhoneView(contact.getId(), manager, now));
            entities.flush();
            entities.clear();
            var stored = entities.find(ContactPhoneView.class, view.getId());
            assertThat(stored.getContactId()).isEqualTo(contact.getId());
            assertThat(stored.getViewerId()).isEqualTo(manager);
            assertThat(stored.getViewedAt()).isCloseTo(now, within(1, java.time.temporal.ChronoUnit.MILLIS));
            assertThat(count("contact_phone_views")).isEqualTo(2);
            jdbc.update("delete from contact_phone_views");
        });
    }
}
