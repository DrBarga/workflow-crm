package dev.workflowcrm.approvals.infrastructure;

import dev.workflowcrm.approvals.api.StatusApprovalRequests.Request;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

final class StatusApprovalRow {
    private StatusApprovalRow() {}

    static Request map(ResultSet row, int number) throws SQLException {
        return new Request(
                row.getObject("id", UUID.class),
                row.getObject("object_id", UUID.class),
                row.getObject("initiator_id", UUID.class),
                row.getObject("confirming_role_id", UUID.class),
                row.getObject("transition_id", UUID.class),
                row.getLong("object_version"),
                row.getString("command"),
                row.getString("status"));
    }
}
