package dev.workflowcrm.shared.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class JdbcEventOutbox implements EventOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcEventOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void append(String key, String type, UUID aggregateId, Object payload) {
        try {
            jdbc.update(
                    """
                    insert into event_outbox(event_key,event_type,aggregate_id,payload)
                    values (?,?,?,?::jsonb) on conflict(event_key) do nothing
                    """,
                    key,
                    type,
                    aggregateId,
                    json.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize domain event", exception);
        }
    }
}
