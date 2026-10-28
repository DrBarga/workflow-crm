package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectMapFiltersIT extends ObjectMapTestSupport {
    @Test
    void everyListFilterAndTheirCombinationHaveTheSameMeaningOnTheMap() throws Exception {
        add("Required", manager, region, "2026-01-15T23:59:59.999999Z", "IN_PROGRESS");
        add("Early", manager, region, "2026-01-14T23:59:59Z", "IN_PROGRESS");
        add("Late", manager, region, "2026-01-16T00:00:00Z", "IN_PROGRESS");
        add("Other manager", colleague, region);
        add("Other region", outsider, otherRegion);
        UUID type = add("Other type", manager, region);
        jdbc.update(
                "update objects set object_type_id=(select id from object_types where code='HANGAR') where id=?", type);
        var combined = Map.of(
                "status",
                "in_progress",
                "managerId",
                manager.toString(),
                "regionId",
                region.toString(),
                "objectType",
                "built_in_premises",
                "q",
                "required",
                "createdFrom",
                "2026-01-15",
                "createdTo",
                "2026-01-15");
        for (var entry : combined.entrySet()) {
            var filter = Map.of(entry.getKey(), entry.getValue());
            assertThat(names(pins(director, BOX, filter)))
                    .containsExactlyInAnyOrderElementsOf(names(page(director, filter)));
        }
        assertThat(names(pins(director, BOX, combined))).containsExactly("Required");
        assertThat(pins(director, BOX, Map.of("status", "UNKNOWN_CODE")).path("content"))
                .isEmpty();
        assertThat(pins(manager, BOX, Map.of("regionId", otherRegion.toString()))
                        .path("content"))
                .isEmpty();
    }

    @Test
    void literalSearchDoesNotRevealRestrictedAddresses() throws Exception {
        add("Магазин Київ", manager, region);
        add("Чужий Київ", colleague, region);
        assertThat(names(pins(manager, BOX, Map.of("q", "приватна")))).containsExactly("Магазин Київ");
        assertThat(pins(contract, BOX, Map.of("q", "приватна")).path("content")).isEmpty();
        assertThat(names(pins(director, BOX, Map.of("q", "КИЇВ")))).hasSize(2);
        add("100%_\\товар", manager, region);
        assertThat(names(pins(director, BOX, Map.of("q", "%_\\")))).containsExactly("100%_\\товар");
        assertThat(pins(director, BOX, Map.of("q", "' OR 1=1 --")).path("content"))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "managerId,invalid",
        "regionId,invalid",
        "status,FOUND;drop",
        "createdFrom,2026-02-30",
        "createdTo,9999-12-31",
        "sort,name",
        "direction,asc",
        "cursor,abc",
        "limit,100",
        "unknown,value"
    })
    void invalidFiltersAndListPaginationControlsReturn422(String field, String value) throws Exception {
        mapRequest(director, BOX, Map.of(field, value))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value(field));
    }

    @Test
    void commonFilterValidationRejectsInvertedDatesRepeatedValuesAndOversizedSearch() throws Exception {
        mapRequest(director, BOX, Map.of("createdFrom", "2026-02-01", "createdTo", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity());
        mapRequest(director, BOX, Map.of("q", "a".repeat(201))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects/map")
                        .cookie(cookie(director))
                        .param("bbox", BOX)
                        .param("status", "FOUND", "REFUSED"))
                .andExpect(status().isUnprocessableEntity());
    }
}
