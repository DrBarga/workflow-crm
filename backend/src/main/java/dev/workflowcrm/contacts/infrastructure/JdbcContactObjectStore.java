package dev.workflowcrm.contacts.infrastructure;

import dev.workflowcrm.contacts.domain.ContactObject;
import dev.workflowcrm.contacts.domain.ContactObjectStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContactObjectStore implements ContactObjectStore {
    private static final String PROJECTION =
            """
            select o.id,o.name,o.manager_id,o.region_id,s.code,s.is_final,o.version
            from objects o join object_statuses s on s.id=o.status_id
            """;
    private static final RowMapper<ContactObject> ROW = (r, n) -> new ContactObject(
            r.getObject("id", UUID.class),
            r.getString("name"),
            r.getObject("manager_id", UUID.class),
            r.getObject("region_id", UUID.class),
            r.getString("code"),
            r.getBoolean("is_final"),
            r.getLong("version"));
    private final NamedParameterJdbcTemplate jdbc;

    JdbcContactObjectStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContactObject> linked(UUID contactId) {
        return jdbc.query(
                PROJECTION
                        + """
                join object_contacts link on link.object_id=o.id
                where link.contact_id=:contact order by o.id
                """,
                Map.of("contact", contactId),
                ROW);
    }

    public Optional<ContactObject> find(UUID objectId) {
        return jdbc.query(PROJECTION + "where o.id=:id", Map.of("id", objectId), ROW).stream()
                .findFirst();
    }

    public List<ContactObject> objects(List<UUID> objectIds) {
        if (objectIds.isEmpty()) return List.of();
        return jdbc.query(
                PROJECTION + "where o.id in (:ids) order by o.id for key share of o", Map.of("ids", objectIds), ROW);
    }

    public void link(UUID contactId, List<UUID> objectIds) {
        if (objectIds.isEmpty()) return;
        var batch = objectIds.stream()
                .map(id -> new MapSqlParameterSource("contact", contactId).addValue("object", id))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("insert into object_contacts(object_id,contact_id) values (:object,:contact)", batch);
    }
}
