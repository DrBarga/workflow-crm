package dev.workflowcrm.approvals.infrastructure;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcEntryPointApprovalRequests implements EntryPointApprovalRequests {
    private final JdbcTemplate jdbc;

    JdbcEntryPointApprovalRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Request pending(UUID object) {
        return jdbc
                .query(
                        "select * from entry_point_approval_requests where object_id=? and status='PENDING' for update",
                        EntryPointApprovalRow::map,
                        object)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Request create(
            UUID object, UUID actor, UUID role, UUID user, BigDecimal longitude, BigDecimal latitude, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into entry_point_approval_requests(id,object_id,initiator_id,confirming_role_id,
                    confirming_user_id,longitude,latitude,object_version) values (?,?,?,?,?,?,?,?)
                """,
                id,
                object,
                actor,
                role,
                user,
                longitude,
                latitude,
                version);
        return lock(id);
    }

    public Request lock(UUID id) {
        return jdbc
                .query(
                        "select * from entry_point_approval_requests where id=? for update",
                        EntryPointApprovalRow::map,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry point request not found"));
    }

    public void finish(UUID id, UUID actor, String decision) {
        if (!java.util.Set.of("APPROVED", "REJECTED", "CANCELLED").contains(decision))
            throw new IllegalArgumentException("Invalid decision");
        if (jdbc.update(
                        "update entry_point_approval_requests set status=?,decided_by_id=?,updated_at=clock_timestamp() where id=? and status='PENDING'",
                        decision,
                        actor,
                        id)
                != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is already resolved");
    }

    public void invalidate(UUID object, UUID except) {
        jdbc.update(
                "update entry_point_approval_requests set status='STALE',updated_at=clock_timestamp() where object_id=? and status='PENDING' and (?::uuid is null or id<>?)",
                object,
                except,
                except);
    }

    public java.util.List<Request> cancelPending(UUID object, UUID actor) {
        return jdbc.query(
                "update entry_point_approval_requests set status='CANCELLED',decided_by_id=?,updated_at=clock_timestamp() where object_id=? and status='PENDING' returning *",
                EntryPointApprovalRow::map,
                actor,
                object);
    }
}
