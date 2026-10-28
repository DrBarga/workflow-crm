package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectListRegionalFiltersIT extends ObjectListTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"status", "managerId", "regionId", "objectType", "createdFrom", "createdTo", "q"})
    void filtersSelectForeignPinsWithoutExpandingTheirFields(String field) throws Exception {
        UUID target = add("Target", outsider, otherRegion, "2026-01-15T12:00:00Z", "IN_PROGRESS");
        jdbc.update(
                "update objects set object_type_id=(select id from object_types where code='HANGAR') where id=?",
                target);
        add("Earlier", manager, region, "2026-01-14T12:00:00Z", "FOUND");
        add("Later", manager, region, "2026-01-16T12:00:00Z", "FOUND");
        String value =
                switch (field) {
                    case "status" -> "IN_PROGRESS";
                    case "managerId" -> outsider.toString();
                    case "regionId" -> otherRegion.toString();
                    case "objectType" -> "HANGAR";
                    case "createdFrom", "createdTo" -> "2026-01-15";
                    default -> "target";
                };
        var rows = page(lead, Map.of(field, value)).path("content");
        var pin = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        rows.forEach(row -> {
            if (row.path("name").asText().equals("Target")) pin.add(row);
        });
        assertThat(pin).hasSize(1);
        assertThat(fields(pin.getFirst())).containsExactlyInAnyOrder("name", "entrance");
        if (!field.startsWith("created")) assertThat(rows).hasSize(1);
    }

    @Test
    void combinedFiltersAndCursorKeepForeignRowsRestricted() throws Exception {
        add("Target A", outsider, otherRegion, "2026-01-15T00:00:00Z", "IN_PROGRESS");
        add("Target B", outsider, otherRegion, "2026-01-15T23:59:59Z", "IN_PROGRESS");
        add("Wrong status", outsider, otherRegion);
        add("Wrong region", manager, region, "2026-01-15T12:00:00Z", "IN_PROGRESS");
        var parameters = new HashMap<>(Map.of(
                "status",
                "IN_PROGRESS",
                "managerId",
                outsider.toString(),
                "regionId",
                otherRegion.toString(),
                "objectType",
                "BUILT_IN_PREMISES",
                "createdFrom",
                "2026-01-15",
                "createdTo",
                "2026-01-15",
                "q",
                "Target",
                "sort",
                "name",
                "direction",
                "asc",
                "limit",
                "1"));
        var first = page(lead, parameters);
        assertThat(names(first)).containsExactly("Target A");
        assertThat(fields(first.path("content").get(0))).containsExactlyInAnyOrder("name", "entrance");
        parameters.put("cursor", first.path("nextCursor").asText());
        var next = page(lead, parameters);
        assertThat(names(next)).containsExactly("Target B");
        assertThat(fields(next.path("content").get(0))).containsExactlyInAnyOrder("name", "entrance");
        assertThat(next.path("hasMore").asBoolean()).isFalse();
    }

    @Test
    void addressSearchMatchesOnlyRegionsWithFullAccess() throws Exception {
        add("Own", colleague, region);
        add("Foreign", outsider, otherRegion);
        assertThat(names(page(lead, Map.of("q", "приватна")))).containsExactly("Own");
        assertThat(page(lead, Map.of("q", "приватна", "regionId", otherRegion.toString()))
                        .path("content"))
                .isEmpty();
        assertThat(names(page(lead, Map.of("q", "foreign")))).containsExactly("Foreign");
        assertThat(page(lead, Map.of("regionId", UUID.randomUUID().toString())).path("content"))
                .isEmpty();
    }
}
