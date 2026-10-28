package dev.workflowcrm.approvals.infrastructure;

import dev.workflowcrm.approvals.api.ObjectApprovalQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcObjectApprovalQuery implements ObjectApprovalQuery {
    private final JdbcTemplate jdbc;

    JdbcObjectApprovalQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Pending> pending(UUID objectId) {
        return jdbc.query(
                """
            select r.id, 'STATUS' as type, s.code as requested_status,
                   null::numeric as longitude, null::numeric as latitude,
                   r.initiator_id,r.confirming_role_id,null::uuid as confirming_user_id,r.created_at
            from status_approval_requests r
            join object_status_transitions t on t.id=r.transition_id
            join object_statuses s on s.id=t.to_status_id
            where r.object_id=? and r.status='PENDING'
            union all
            select id,'ENTRY_POINT',null,longitude,latitude,initiator_id,confirming_role_id,confirming_user_id,created_at
            from entry_point_approval_requests where object_id=? and status='PENDING'
            order by created_at,id
            """,
                (r, n) -> new Pending(
                        r.getObject("id", UUID.class),
                        r.getString("type"),
                        r.getString("requested_status"),
                        r.getBigDecimal("longitude"),
                        r.getBigDecimal("latitude"),
                        r.getObject("initiator_id", UUID.class),
                        r.getObject("confirming_role_id", UUID.class),
                        r.getObject("confirming_user_id", UUID.class),
                        r.getTimestamp("created_at").toInstant()),
                objectId,
                objectId);
    }
}
