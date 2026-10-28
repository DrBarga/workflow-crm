package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.ObjectContactSelectionStore;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectContactSelectionStore implements ObjectContactSelectionStore {
    private final JdbcTemplate jdbc;

    JdbcObjectContactSelectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(UUID object, UUID contact) {
        var current = jdbc.query(
                "select contact_id from object_contacts where object_id=? for update",
                (r, n) -> r.getObject(1, UUID.class),
                object);
        if (current.size() > 1)
            throw new UnprocessableInputException("contactId", "Use the contacts section to manage multiple contacts");
        if (current.size() == 1 && current.getFirst().equals(contact)) return;
        jdbc.update("delete from object_contacts where object_id=?", object);
        jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", object, contact);
    }
}
