package dev.workflowcrm.audit.infrastructure;

import dev.workflowcrm.audit.api.SecurityUnlockAudit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcSecurityUnlockAudit implements SecurityUnlockAudit {
    private final JdbcTemplate jdbc;

    JdbcSecurityUnlockAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID request(UUID administratorId, UUID userId, Action action, UUID blockedDeviceId) {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                "insert into security_unlock_events (id, administrator_id, user_id, action, blocked_device_id) values (?, ?, ?, ?, ?)",
                eventId,
                administratorId,
                userId,
                action.name(),
                blockedDeviceId);
        return eventId;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID eventId, Outcome outcome) {
        int updated = jdbc.update(
                "update security_unlock_events set outcome = ?, updated_at = current_timestamp where id = ? and outcome = 'REQUESTED'",
                outcome.name(),
                eventId);
        if (updated != 1) {
            throw new IllegalStateException("Unlock audit event cannot be completed");
        }
    }
}
