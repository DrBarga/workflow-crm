package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.api.ObjectHistoryEvents;
import dev.workflowcrm.objects.domain.ObjectHistory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaObjectHistoryEvents implements ObjectHistoryEvents {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectHistoryRepository history;

    JpaObjectHistoryEvents(JdbcTemplate jdbc, ObjectMapper json, ObjectHistoryRepository history) {
        this.jdbc = jdbc;
        this.json = json;
        this.history = history;
    }

    public void record(UUID objectId, UUID actorId, long objectVersion, Type type, Object before, Object after) {
        Objects.requireNonNull(type);
        if (version(objectId) != objectVersion) throw new IllegalStateException("Object version changed");
        save(objectId, actorId, objectVersion, type, before, after);
    }

    public void record(UUID objectId, UUID actorId, Type type, Object before, Object after) {
        Objects.requireNonNull(type);
        save(objectId, actorId, version(objectId), type, before, after);
    }

    private long version(UUID objectId) {
        return Objects.requireNonNull(
                jdbc.queryForObject("select version from objects where id=? for update", Long.class, objectId));
    }

    private void save(UUID objectId, UUID actorId, long objectVersion, Type type, Object before, Object after) {
        history.save(new ObjectHistory(
                objectId,
                actorId,
                actorId == null ? ObjectHistory.ActorType.SYSTEM : ObjectHistory.ActorType.USER,
                type.name(),
                value(before),
                value(after),
                objectVersion));
    }

    private String value(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot encode history value", e);
        }
    }
}
