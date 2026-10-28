package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.Contact;
import dev.workflowcrm.contacts.domain.ContactNotFoundException;
import dev.workflowcrm.contacts.domain.ContactStore;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JpaContactStore implements ContactStore {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;

    JpaContactStore(EntityManager entities, JdbcTemplate jdbc) {
        this.entities = entities;
        this.jdbc = jdbc;
    }

    public Contact find(UUID id, boolean lock) {
        if (lock
                && jdbc.query(
                                "select id from contacts where id=? and deleted_at is null for update",
                                (row, index) -> row.getObject(1, UUID.class),
                                id)
                        .isEmpty()) throw new ContactNotFoundException();
        var contact = entities.find(Contact.class, id);
        if (contact == null || contact.getDeletedAt() != null) throw new ContactNotFoundException();
        return contact;
    }

    public Contact save(Contact contact) {
        if (contact.getId() == null) entities.persist(contact);
        entities.flush();
        return contact;
    }

    public boolean activeType(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from contact_types where id=? and is_active)", Boolean.class, id));
    }
}
