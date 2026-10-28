package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectListFiltersIT extends ObjectListTestSupport {
    @Test
    void allFiltersCombineAndDateBoundariesUseInclusiveUtcDays() throws Exception {
        add("Потрібний", manager, region, "2026-01-15T23:59:59.999999Z", "IN_PROGRESS");
        add("Ранній", manager, region, "2026-01-14T23:59:59Z", "IN_PROGRESS");
        add("Пізній", manager, region, "2026-01-16T00:00:00Z", "IN_PROGRESS");
        add("Інший менеджер", colleague, region, "2026-01-15T12:00:00Z", "IN_PROGRESS");
        add("Інший статус", manager, region);
        add("Інший регіон", outsider, otherRegion);
        UUID type = add("Інший тип", manager, region, "2026-01-15T12:00:00Z", "IN_PROGRESS");
        jdbc.update(
                "update objects set object_type_id=(select id from object_types where code='HANGAR') where id=?", type);
        var filters = Map.of(
                "status",
                "in_progress",
                "managerId",
                manager.toString(),
                "regionId",
                region.toString(),
                "objectType",
                "built_in_premises",
                "createdFrom",
                "2026-01-15",
                "createdTo",
                "2026-01-15");
        assertThat(names(page(director, filters))).containsExactly("Потрібний");
        assertThat(page(director, Map.of("status", "UNKNOWN_CODE")).path("content"))
                .isEmpty();
        assertThat(page(director, Map.of("regionId", UUID.randomUUID().toString()))
                        .path("content"))
                .isEmpty();
    }

    @Test
    void searchIsCaseInsensitiveLiteralAndDoesNotRevealRestrictedAddresses() throws Exception {
        add("Магазин Київ", manager, region);
        add("Чужий Київ", colleague, region);
        assertThat(names(page(director, Map.of("q", "КИЇВ")))).hasSize(2);
        assertThat(names(page(director, Map.of("q", "ПРИВАТНА")))).hasSize(2);
        assertThat(names(page(manager, Map.of("q", "приватна")))).containsExactly("Магазин Київ");
        assertThat(page(contract, Map.of("q", "приватна")).path("content")).isEmpty();
        add("100%_\\товар", manager, region);
        assertThat(names(page(director, Map.of("q", "%_\\")))).containsExactly("100%_\\товар");
        assertThat(page(director, Map.of("q", "' OR 1=1 --")).path("content")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "limit,0",
        "limit,101",
        "limit,abc",
        "sort,address",
        "direction,sideways",
        "managerId,invalid",
        "managerId,1-1-1-1-1",
        "regionId,invalid",
        "status,FOUND;drop",
        "createdFrom,2026-02-30",
        "createdTo,9999-12-31",
        "unknown,value"
    })
    void invalidParametersReturnFieldViolation(String field, String value) throws Exception {
        request(director, Map.of(field, value))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value(field));
    }

    @Test
    void rangesDuplicateParametersAndLongSearchAreRejected() throws Exception {
        request(director, Map.of("createdFrom", "2026-02-01", "createdTo", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity());
        request(director, Map.of("q", "a".repeat(201))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects").cookie(cookie(director)).param("limit", "1", "2"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects")).andExpect(status().isUnauthorized());
    }
}
