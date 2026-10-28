package dev.workflowcrm.approvals.infrastructure;

import dev.workflowcrm.approvals.api.EntryPointApprovalRequests.Request;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class EntryPointApprovalRow {
    private EntryPointApprovalRow() {}

    static Request map(ResultSet r, int n) throws SQLException {
        return new Request(
                r.getObject("id", UUID.class),
                r.getObject("object_id", UUID.class),
                r.getObject("initiator_id", UUID.class),
                r.getObject("confirming_role_id", UUID.class),
                r.getObject("confirming_user_id", UUID.class),
                r.getBigDecimal("longitude"),
                r.getBigDecimal("latitude"),
                r.getLong("object_version"),
                r.getString("status"),
                r.getTimestamp("created_at").toInstant());
    }
}
