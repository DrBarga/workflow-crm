package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ObjectEntryPointSideStatusIT extends ObjectEditTestSupport {
    static Stream<Arguments> stages() {
        return Stream.of("MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR")
                .flatMap(role -> Stream.of("PAUSED", "REFUSED")
                        .flatMap(side -> Stream.of("IN_PROGRESS", "IC_PREPARATION")
                                .map(previous -> Arguments.of(role, side, previous))));
    }

    @ParameterizedTest
    @MethodSource("stages")
    void previousStageControlsDirectChangeOrApprovalForEveryPointEditor(String role, String side, String previous)
            throws Exception {
        UUID object = add("Точка входу", manager, region);
        state(object, side);
        String column = side.equals("PAUSED") ? "status_before_pause_id" : "status_before_refusal_id";
        jdbc.update(
                "update objects set " + column + "=(select id from object_statuses where code=?) where id=?",
                previous,
                object);
        var before = card(manager, object);
        var result = response(point(actor(role), object, coordinates()).andExpect(status().isOk()));
        boolean approval = previous.equals("IC_PREPARATION");
        assertThat(result.path("saved").asBoolean()).isEqualTo(!approval);
        if (approval) {
            UUID request = UUID.fromString(result.path("approvalRequestId").asText());
            assertThat(version(object)).isZero();
            assertThat(card(manager, object).path("entrance")).isEqualTo(before.path("entrance"));
            assertThat(card(manager, object).path("googleMapsUrl")).isEqualTo(before.path("googleMapsUrl"));
            UUID approver = role.equals("MANAGER") ? lead : manager;
            if (role.equals("MANAGER")) {
                assertThat(jdbc.queryForObject(
                                "select confirming_role_id from entry_point_approval_requests where id=?",
                                UUID.class,
                                request))
                        .isEqualTo(
                                jdbc.queryForObject("select id from roles where code='REGIONAL_MANAGER'", UUID.class));
            } else {
                assertThat(jdbc.queryForObject(
                                "select confirming_user_id from entry_point_approval_requests where id=?",
                                UUID.class,
                                request))
                        .isEqualTo(approver);
            }
            decide(approver, object, request, "approve").andExpect(status().isOk());
        } else assertThat(count("entry_point_approval_requests")).isZero();
        assertThat(version(object)).isEqualTo(1);
        assertThat(state(object)).isEqualTo(side);
        var after = card(manager, object);
        assertThat(after.path("entrance").path("longitude").decimalValue()).isEqualByComparingTo("30.5234");
        assertThat(after.path("googleMapsUrl").asText())
                .isNotEqualTo(before.path("googleMapsUrl").asText());
        assertThat(after.path("address")).isEqualTo(before.path("address"));
        assertThat(after.path("regionId")).isEqualTo(before.path("regionId"));
    }
}
