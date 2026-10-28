package dev.workflowcrm.audit.infrastructure;

import dev.workflowcrm.audit.api.ObjectDuplicateAudit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectDuplicateAudit implements ObjectDuplicateAudit {
    private final JdbcTemplate jdbc;

    JdbcObjectDuplicateAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> cancellationHash(UUID actor, String key) {
        return jdbc
                .query(
                        "select request_hash from object_duplicate_decisions where actor_id=? and request_key=? and decision='CANCELLED'",
                        (rs, n) -> rs.getString(1),
                        actor,
                        key)
                .stream()
                .findFirst();
    }

    public void record(UUID actor, String key, String hash, UUID object, long total, List<UUID> nearest) {
        jdbc.update(
                "insert into object_duplicate_decisions(actor_id,request_key,request_hash,object_id,decision,match_count,nearest_object_ids) values (?,?,?,?,?,?,?::uuid[])",
                actor,
                key,
                hash,
                object,
                object == null ? "CANCELLED" : "CREATED",
                total,
                "{" + String.join(",", nearest.stream().map(UUID::toString).toList()) + "}");
    }
}
