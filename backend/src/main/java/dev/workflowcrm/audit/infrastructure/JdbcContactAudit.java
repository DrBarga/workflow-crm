package dev.workflowcrm.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.audit.api.ContactAudit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcContactAudit implements ContactAudit {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcContactAudit(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(
            UUID contactId,
            UUID actorId,
            long version,
            Action action,
            List<String> changedFields,
            List<UUID> linkedObjectIds,
            Map<String, Change> changedValues) {
        try {
            jdbc.update(
                    """
                    insert into contact_history(contact_id,actor_id,contact_version,event_type,
                    changed_fields,linked_object_ids,changed_values)
                    values (?,?,?,?,?::jsonb,?::jsonb,?::jsonb)
                    """,
                    contactId,
                    actorId,
                    version,
                    action.name(),
                    json.writeValueAsString(changedFields),
                    json.writeValueAsString(linkedObjectIds),
                    json.writeValueAsString(changedValues));
        } catch (JsonProcessingException | DataAccessException exception) {
            // Database diagnostics may contain the complete audit row with contact values.
            throw new IllegalStateException("Cannot record contact history");
        }
    }
}
