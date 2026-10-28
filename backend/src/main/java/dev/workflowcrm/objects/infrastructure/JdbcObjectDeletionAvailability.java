package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectDeletionAvailability;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectDeletionAvailability implements ObjectDeletionAvailability {
    private final JdbcTemplate jdbc;

    JdbcObjectDeletionAvailability(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean pending(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from object_deletion_operations where object_id=? and state='PENDING')",
                Boolean.class,
                id));
    }
}
