package dev.workflowcrm.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.audit.api.ObjectImportAudit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcObjectImportAudit implements ObjectImportAudit {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcObjectImportAudit(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(UUID job, UUID actor, String event, Object detail) {
        try {
            jdbc.update(
                    "insert into object_import_audit(import_id,actor_id,event_type,detail) values(?,?,?,?::jsonb)",
                    job,
                    actor,
                    event,
                    json.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot record import audit", e);
        }
    }
}
