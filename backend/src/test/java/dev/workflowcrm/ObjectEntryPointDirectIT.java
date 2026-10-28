package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEntryPointDirectIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR"})
    void earlyChangeWritesCoordinatesHistoryAuditAndMaps(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var oldMaps = card(manager, object).path("googleMapsUrl").asText();
        var result = response(point(actor(role), object, coordinates()).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(result.path("version").asLong()).isEqualTo(1);
        var card = card(manager, object);
        assertThat(card.path("entrance").path("longitude").decimalValue()).isEqualByComparingTo("30.5234");
        assertThat(card.path("googleMapsUrl").asText())
                .contains("50.4501", "30.5234")
                .isNotEqualTo(oldMaps);
        assertThat(card.path("address").asText()).isEqualTo("Приватна адреса");
        assertThat(count("entry_point_approval_requests")).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name in ('entrance','googleMapsUrl')",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select event_type from object_status_audit", String.class))
                .isEqualTo("ENTRY_POINT_CHANGED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "PAUSED"})
    void earlyWorkAndPauseChangeWithoutApproval(String stage) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, stage);
        if (stage.equals("PAUSED"))
            jdbc.update(
                    "update objects set status_before_pause_id=(select id from object_statuses where code='IN_PROGRESS') where id=?",
                    object);
        point(manager, object, coordinates()).andExpect(status().isOk());
        assertThat(count("entry_point_approval_requests")).isZero();
        assertThat(version(object)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEO", "CONTRACT_MANAGER", "SUPERADMIN"})
    void unlistedRolesCannotMoveEntrance(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        point(actor(role), object, coordinates()).andExpect(status().isForbidden());
        assertThat(version(object)).isZero();
    }

    @Test
    void pointBodyOnlyAcceptsValidCoordinatesWithinUkraine() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        for (var body : List.of(
                Map.<String, Object>of("longitude", 181, "latitude", 50),
                Map.<String, Object>of("longitude", 0, "latitude", 0),
                Map.<String, Object>of("longitude", "30.5", "latitude", 50),
                Map.<String, Object>of("longitude", 30.5),
                Map.<String, Object>of("longitude", 30.52340001, "latitude", 50.45)))
            point(manager, object, body).andExpect(status().isUnprocessableEntity());
        var body = new LinkedHashMap<>(coordinates());
        body.put("version", 0);
        point(manager, object, body).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
    }

    @Test
    void unchangedPointDoesNotWriteAnything() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var result = response(point(manager, object, Map.of("longitude", 30.5, "latitude", 50.4))
                .andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isFalse();
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
    }
}
