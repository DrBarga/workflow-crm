package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectEditLifecycleValidationIT extends ObjectEditTestSupport {
    @Test
    void signingDateCannotSkipStatusTableAndBothDatesCannotAdvanceAtOnce() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(contract, object, change(object, "contractSignedDate", "2026-10-07"))
                .andExpect(status().isUnprocessableEntity());
        state(object, "CONTRACT_APPROVAL");
        jdbc.update("update objects set contract_type_id=? where id=?", type(), object);
        var body = change(object, "contractSignedDate", "2026-10-07");
        body.put("openingActualDate", "2026-10-08");
        patch(lead, object, body).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
    }

    @Test
    void invalidChronologyAndClearingLifecycleDatesAreRejected() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "CONTRACT_SIGNED");
        contractType(object);
        patch(lead, object, change(object, "openingActualDate", "2026-09-30"))
                .andExpect(status().isUnprocessableEntity());
        patch(lead, object, change(object, "contractSignedDate", null)).andExpect(status().isUnprocessableEntity());
        state(object, "OPENED");
        jdbc.update("update objects set opening_date='2026-10-07' where id=?", object);
        patch(lead, object, change(object, "openingActualDate", null)).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
    }

    @Test
    void inactiveHistoricalTypeDoesNotBlockUnrelatedPatch() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        contractType(object);
        jdbc.update("update contract_types set is_active=false where id=?", type());
        try {
            patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select contract_type_id from objects where id=?", UUID.class, object))
                    .isEqualTo(type());
        } finally {
            jdbc.update("update contract_types set is_active=true where id=?", type());
        }
    }

    @Test
    void statusChangeInvalidatesPendingPointRequest() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        var body = Map.of("version", version(object), "status", "IN_PROGRESS");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/objects/{id}/status", object)
                        .cookie(cookie(lead), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("STALE");
        decide(lead, object, request, "approve").andExpect(status().isConflict());
    }

    @Test
    void refusalCancelsPendingPointRequestAndNotifiesInitiator() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID request = request(manager, object);
        UUID reason = jdbc.queryForObject(
                "select id from refusal_reasons where is_active=true and is_system_only=false order by code limit 1",
                UUID.class);
        var body = Map.of("version", version(object), "status", "REFUSED", "refusalReasonId", reason);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/objects/{id}/status", object)
                        .cookie(cookie(lead), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk());
        assertThat(requestStatus(request)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where recipient_id=? and event_type='OBJECT_ENTRY_POINT_APPROVAL_CANCELLED'",
                        Integer.class,
                        manager))
                .isEqualTo(1);
    }
}
