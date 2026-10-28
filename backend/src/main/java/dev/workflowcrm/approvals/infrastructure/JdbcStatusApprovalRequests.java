package dev.workflowcrm.approvals.infrastructure;

import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class JdbcStatusApprovalRequests implements StatusApprovalRequests {
    private final JdbcTemplate jdbc;

    JdbcStatusApprovalRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Request create(UUID object, UUID actor, UUID role, UUID transition, long version, String command) {
        var existing = jdbc.query(
                """
                select id from status_approval_requests where object_id=? and status='PENDING'
                """,
                (r, n) -> r.getObject(1, UUID.class),
                object);
        if (!existing.isEmpty()) {
            var request = lock(existing.getFirst());
            if (request.initiatorId().equals(actor)
                    && request.transitionId().equals(transition)
                    && request.objectVersion() == version
                    && request.command().equals(command)) return request;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object already has a pending status request");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into status_approval_requests
                (id,object_id,initiator_id,confirming_role_id,transition_id,object_version,command)
                values (?,?,?,?,?,?,?)
                """,
                id,
                object,
                actor,
                role,
                transition,
                version,
                command);
        return lock(id);
    }

    public Request lock(UUID id) {
        return jdbc
                .query(
                        """
                select * from status_approval_requests where id=? for update
                """,
                        StatusApprovalRow::map,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status request not found"));
    }

    public void finish(UUID id, UUID actor, String decision) {
        if (!java.util.Set.of("APPROVED", "REJECTED", "CANCELLED").contains(decision))
            throw new IllegalArgumentException("Invalid approval decision");
        int changed = jdbc.update(
                """
                update status_approval_requests set status=?,decided_by_id=?,updated_at=clock_timestamp()
                where id=? and status='PENDING'
                """,
                decision,
                actor,
                id);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Status request is already resolved");
    }

    public void invalidate(UUID object, UUID except) {
        jdbc.update(
                """
                update status_approval_requests set status='STALE',updated_at=clock_timestamp()
                where object_id=? and status='PENDING' and (?::uuid is null or id<>?)
                """,
                object,
                except,
                except);
    }

    public java.util.List<Request> cancelPending(UUID object, UUID actor, UUID except) {
        return jdbc.query(
                """
                update status_approval_requests set status='CANCELLED',decided_by_id=?,updated_at=clock_timestamp()
                where object_id=? and status='PENDING' and (?::uuid is null or id<>?)
                returning *
                """,
                StatusApprovalRow::map,
                actor,
                object,
                except,
                except);
    }
}
