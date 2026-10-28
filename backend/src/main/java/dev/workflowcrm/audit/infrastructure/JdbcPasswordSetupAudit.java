package dev.workflowcrm.audit.infrastructure;

import dev.workflowcrm.audit.api.PasswordSetupAudit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPasswordSetupAudit implements PasswordSetupAudit {
    private final JdbcTemplate jdbc;

    JdbcPasswordSetupAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID userId, String deviceId) {
        for (String action : new String[] {"INITIAL_PASSWORD_SET", "INVITATION_DEVICE_TRUSTED"}) {
            jdbc.update(
                    "insert into password_setup_events (id, user_id, device_id, action) values (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    userId,
                    deviceId,
                    action);
        }
    }
}
