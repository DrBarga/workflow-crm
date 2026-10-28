package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectMapBoundsIT extends ObjectMapTestSupport {
    @Test
    void bboxIncludesItsEdgesAndExcludesAllFourDirections() throws Exception {
        UUID southwest = add("Southwest", manager, region), northeast = add("Northeast", manager, region);
        jdbc.update("update objects set entrance_longitude=30,entrance_latitude=50 where id=?", southwest);
        jdbc.update("update objects set entrance_longitude=31,entrance_latitude=51 where id=?", northeast);
        for (var coordinate : List.of(
                new double[] {29.9999999, 50.5},
                new double[] {31.0000001, 50.5},
                new double[] {30.5, 49.9999999},
                new double[] {30.5, 51.0000001})) {
            UUID outside = add("Outside", manager, region);
            jdbc.update(
                    "update objects set entrance_longitude=?,entrance_latitude=? where id=?",
                    coordinate[0],
                    coordinate[1],
                    outside);
        }
        var result = pins(director);
        assertThat(names(result)).containsExactlyInAnyOrder("Southwest", "Northeast");
        assertThat(result.path("truncated").asBoolean()).isFalse();
        assertThat(pins(director, "0,0,1,1", Map.of()).path("content")).isEmpty();
        assertThat(names(pins(director, "3e1,5e1,3.1e1,5.1e1", Map.of()))).hasSize(2);
    }

    @Test
    void pendingDeletionAndFutureObjectsAreHiddenLikeTheList() throws Exception {
        UUID pending = add("Pending", manager, region);
        jdbc.update(
                "insert into object_deletion_operations(object_id,actor_id,actor_name) values (?,?,'Administrator')",
                pending,
                admin);
        add("Future", manager, region, "9998-01-01T00:00:00Z", "FOUND");
        add("Visible", manager, region);
        assertThat(names(pins(director))).containsExactly("Visible");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "30,50,31",
                "30,50,31,51,52",
                "31,50,30,51",
                "30,51,31,50",
                "30,50,30,51",
                "30,50,31,50",
                "-181,50,31,51",
                "30,-91,31,51",
                "30,50,181,51",
                "30,50,31,91",
                "NaN,50,31,51",
                "30,50,Infinity,51",
                "30,50,1e999,51",
                "a,50,31,51",
                "30,,31,51",
                "0x1p0,50,31,51",
                "30d,50,31,51",
                "30,50,180.00000000000000001,51",
                "30,50,31,90.00000000000000001",
                "0,0,1e-999999999,1"
            })
    void malformedBoundsReturn422WithBboxViolation(String bbox) throws Exception {
        mapRequest(director, bbox, Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("bbox"));
    }

    @Test
    void missingRepeatedAndOversizedBboxAreRejectedAndAnonymousRequestIs401() throws Exception {
        mapRequest(director, null, Map.of()).andExpect(status().isUnprocessableEntity());
        mapRequest(director, "1".repeat(257), Map.of()).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects/map").cookie(cookie(director)).param("bbox", BOX, BOX))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects/map").param("bbox", BOX)).andExpect(status().isUnauthorized());
    }
}
