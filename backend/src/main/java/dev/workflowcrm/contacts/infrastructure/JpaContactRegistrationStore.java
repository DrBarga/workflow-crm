package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.Contact;
import dev.workflowcrm.contacts.domain.ContactRegistrationStore;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JpaContactRegistrationStore implements ContactRegistrationStore {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;

    JpaContactRegistrationStore(EntityManager entities, JdbcTemplate jdbc) {
        this.entities = entities;
        this.jdbc = jdbc;
    }

    public boolean canEditObject(UUID objectId, UUID actorId, UUID regionId, boolean regionalManager, boolean network) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from objects where id=? and (? or region_id=?) and (? or manager_id=?))",
                Boolean.class,
                objectId,
                network,
                regionId,
                regionalManager,
                actorId));
    }

    public UUID save(Contact contact) {
        entities.persist(contact);
        entities.flush();
        return contact.getId();
    }

    public void link(UUID objectId, UUID contactId) {
        jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", objectId, contactId);
    }
}
