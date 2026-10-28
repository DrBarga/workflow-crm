package dev.workflowcrm.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.audit.api.ObjectStatusAudit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcObjectStatusAudit implements ObjectStatusAudit {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcObjectStatusAudit(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(UUID object, UUID actor, long version, String type, Object detail) {
        try {
            jdbc.update(
                    """
                    insert into object_status_audit(object_id,actor_id,object_version,event_type,detail)
                    values (?,?,?,?,?::jsonb)
                    """,
                    object,
                    actor,
                    version,
                    type,
                    json.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot record object status audit", exception);
        }
    }
}
