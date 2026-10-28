package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectExportFiltersIT extends ObjectExportTestSupport {
    @Test
    void everyFilterMatchesListAndCombinedFiltersApplyBeforeExport() throws Exception {
        UUID match = add("Магазин Київ", manager, region, "2026-02-01T12:00:00Z", "FOUND");
        add("Магазин Дніпро", outsider, otherRegion, "2026-03-01T12:00:00Z", "IN_PROGRESS");
        var filters = List.of(
                Map.of("status", "FOUND"),
                Map.of("managerId", manager.toString()),
                Map.of("regionId", region.toString()),
                Map.of("objectType", "BUILT_IN_PREMISES"),
                Map.of("q", "Київ"),
                Map.of("createdFrom", "2026-02-01"),
                Map.of("createdTo", "2026-02-28"),
                Map.of(
                        "status",
                        "FOUND",
                        "managerId",
                        manager.toString(),
                        "regionId",
                        region.toString(),
                        "objectType",
                        "BUILT_IN_PREMISES",
                        "q",
                        "Київ",
                        "createdFrom",
                        "2026-02-01",
                        "createdTo",
                        "2026-02-28"));
        for (var f : filters) {
            var list = page(director, f);
            String csv = csv(director, f);
            assertThat(csv.lines().count() - 1).isEqualTo(list.path("content").size());
            for (var row : list.path("content"))
                assertThat(csv).contains(row.path("id").asText());
        }
        assertThat(csv(director, filters.getLast())).contains(match.toString()).doesNotContain("Дніпро");
        assertThat(csv(director, Map.of("status", "UNKNOWN")).lines().count()).isEqualTo(1);
    }

    @Test
    void searchUsesLiteralWildcardsAndCannotInjectSql() throws Exception {
        add("Шаблон 100%_\"Київ\"", manager, region);
        add("Звичайний", manager, region);
        assertThat(csv(director, Map.of("q", "100%_"))).contains("Шаблон").doesNotContain("Звичайний");
        assertThat(csv(director, Map.of("q", "' OR 1=1 --")).lines().count()).isEqualTo(1);
    }

    @Test
    void sortingMatchesListIncludingNullsAndDirection() throws Exception {
        add("Бета", manager, region);
        add("Альфа", manager, region);
        for (String sort : List.of("name", "status", "createdAt", "investmentCommitteeDate", "contractSignedDate"))
            for (String direction : List.of("asc", "desc")) {
                var f = Map.of("sort", sort, "direction", direction);
                var names = names(page(director, f));
                var csv = csv(director, f);
                assertThat(csv.indexOf(names.get(0))).isLessThan(csv.indexOf(names.get(1)));
            }
    }

    @Test
    void snapshotExcludesNewAndPendingDeletionObjects() throws Exception {
        var visible = add("До запиту", manager, region);
        var pending = add("Видалення", manager, region);
        accepted(pending);
        UUID id = enqueue(director, "CSV", Map.of());
        add("Після запиту", manager, region, "2099-01-01T00:00:00Z", "FOUND");
        exportProcessor.processNext();
        String text = new String(content(id), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains(visible.toString()).doesNotContain("Видалення", "Після запиту");
    }

    @ParameterizedTest
    @CsvSource({
        "format,PDF",
        "sort,unknown",
        "direction,sideways",
        "limit,50",
        "cursor,abc",
        "bbox,30",
        "unknown,1",
        "managerId,invalid",
        "regionId,invalid",
        "createdFrom,2026-02-30",
        "createdTo,9999-01-01",
        "status,A-B"
    })
    void invalidParametersAreRejected(String field, String value) throws Exception {
        var body = new HashMap<>(Map.of("format", "CSV"));
        body.put(field, value);
        export(director, body).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_exports")).isZero();
    }

    @Test
    void invalidDateRangeAndOversizedSearchAreRejected() throws Exception {
        export(director, Map.of("format", "CSV", "createdFrom", "2026-03-01", "createdTo", "2026-02-01"))
                .andExpect(status().isUnprocessableEntity());
        export(director, Map.of("format", "CSV", "q", "q".repeat(201))).andExpect(status().isUnprocessableEntity());
    }
}
