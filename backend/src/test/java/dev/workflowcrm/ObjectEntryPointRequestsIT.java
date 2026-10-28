package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEntryPointRequestsIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {"IC_PREPARATION", "COMMERCIAL_TERMS_APPROVAL", "CONTRACT_APPROVAL", "CONTRACT_SIGNED", "OPENED"})
    void lateManagerRequestKeepsEntranceAndNotifiesLead(String stage) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, stage);
        UUID request = request(manager, object);
        assertThat(version(object)).isZero();
        var pending = card(manager, object).path("pendingRequests");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).path("type").asText()).isEqualTo("ENTRY_POINT");
        assertThat(pending.get(0)
                        .path("requested")
                        .path("entrance")
                        .path("longitude")
                        .decimalValue())
                .isEqualByComparingTo("30.5234");
        assertThat(card(manager, object).path("entrance").path("longitude").decimalValue())
                .isEqualByComparingTo("30.5");
        assertThat(jdbc.queryForObject(
                        "select confirming_role_id from entry_point_approval_requests where id=?", UUID.class, request))
                .isEqualTo(jdbc.queryForObject("select id from roles where code='REGIONAL_MANAGER'", UUID.class));
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=?", Integer.class, lead))
                .isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR"})
    void leadAndDirectorRequestApprovalFromAssignedManager(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(actor(role), object);
        assertThat(jdbc.queryForObject(
                        "select confirming_user_id from entry_point_approval_requests where id=?", UUID.class, request))
                .isEqualTo(manager);
        decide(manager, object, request, "approve").andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("APPROVED");
        assertThat(version(object)).isEqualTo(1);
        assertThat(card(manager, object).path("pendingRequests")).isEmpty();
    }

    @Test
    void samePendingRequestIsIdempotentAndOtherCoordinatesConflict() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        assertThat(request(manager, object)).isEqualTo(request);
        point(manager, object, Map.of("longitude", 30.53, "latitude", 50.45)).andExpect(status().isConflict());
        assertThat(count("entry_point_approval_requests")).isEqualTo(1);
        assertThat(count("user_notifications")).isEqualTo(1);
    }

    @Test
    void missingLeadUsesDirectorFallback() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        jdbc.update("update users set status='DISABLED' where id=?", lead);
        UUID request = request(manager, object);
        assertThat(jdbc.queryForObject(
                        "select confirming_user_id from entry_point_approval_requests where id=?", UUID.class, request))
                .isEqualTo(director);
        decide(director, object, request, "approve").andExpect(status().isOk());
    }

    @Test
    void missingAllApproversDoesNotCreateRequest() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        jdbc.update("update users set status='DISABLED' where id in (?,?)", lead, director);
        point(manager, object, coordinates()).andExpect(status().isConflict());
        assertThat(count("entry_point_approval_requests")).isZero();
    }
}
