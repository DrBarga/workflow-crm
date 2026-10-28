package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectCardRequestsIT extends ObjectCardTestSupport {
    @Test
    void pendingStatusRequestReturnsTypedTargetActorsAndDatabaseTime() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update(
                """
            insert into status_approval_requests(object_id,initiator_id,confirming_role_id,transition_id,object_version,command,created_at)
            select ?,?,t.confirming_role_id,t.id,0,'internal private command','2026-10-07T10:00:00Z'
            from object_status_transitions t where t.code='REQUEST_FOUND_TO_IC'
            """,
                object,
                manager);
        var c = card(manager, object);
        assertThat(c.path("pendingRequests")).hasSize(1);
        var r = c.path("pendingRequests").get(0);
        assertThat(r.path("type").asText()).isEqualTo("STATUS");
        assertThat(r.path("requested").path("status").asText()).isEqualTo("IC_PREPARATION");
        assertThat(r.path("initiatorId").asText()).isEqualTo(manager.toString());
        assertThat(r.path("confirmingRoleId").asText()).isNotBlank();
        assertThat(r.path("requestedAt").asText()).isEqualTo("2026-10-07T10:00:00Z");
        assertThat(c.toString()).doesNotContain("internal private command");
        assertThat(fields(card(colleague, object))).containsExactlyInAnyOrder("name", "entrance");
    }

    @Test
    void pendingEntryPointRequestReturnsRequestedCoordinatesWithoutMovingCurrentPoint() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update(
                """
            insert into entry_point_approval_requests(object_id,initiator_id,confirming_user_id,longitude,latitude,object_version,created_at)
            values (?,?,?,31.1,49.2,0,'2026-10-07T11:00:00Z')
            """,
                object,
                lead,
                manager);
        var c = card(manager, object);
        var r = c.path("pendingRequests").get(0);
        assertThat(r.path("type").asText()).isEqualTo("ENTRY_POINT");
        assertThat(r.path("requested").path("entrance").path("longitude").asDouble())
                .isEqualTo(31.1);
        assertThat(r.path("confirmingUserId").asText()).isEqualTo(manager.toString());
        assertThat(c.path("entrance").path("longitude").asDouble()).isEqualTo(30.5);
        assertThat(c.path("version").asInt()).isZero();
    }

    @Test
    void resolvedStatusAndEntryRequestsAreNotReturned() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update(
                """
            insert into entry_point_approval_requests(object_id,initiator_id,confirming_role_id,longitude,latitude,object_version,status)
            select ?,?,id,31.1,49.2,0,'REJECTED' from roles where code='REGIONAL_MANAGER'
            """,
                object,
                manager);
        assertThat(card(manager, object).path("pendingRequests")).isEmpty();
    }
}
