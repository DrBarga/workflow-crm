package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectListCursorIT extends ObjectListTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"createdAt", "name", "status", "investmentCommitteeDate", "contractSignedDate"})
    void tiesAndMultipleNullsHaveNoGapsOrDuplicatesInEitherDirection(String sort) throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 7; i++)
            ids.add(add("Однакова назва", manager, region).toString());
        for (String direction : List.of("asc", "desc")) {
            var parameters = new HashMap<>(Map.of("sort", sort, "direction", direction, "limit", "2"));
            var observed = new ArrayList<String>();
            for (int i = 0; i < 4; i++) {
                var result = page(director, parameters);
                result.path("content").forEach(r -> observed.add(r.path("id").asText()));
                if (result.path("hasMore").asBoolean())
                    parameters.put("cursor", result.path("nextCursor").asText());
                else break;
            }
            var expected = new ArrayList<>(ids);
            expected.sort(direction.equals("asc") ? Comparator.naturalOrder() : Comparator.reverseOrder());
            assertThat(observed).containsExactlyElementsOf(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "limit",
                "sort",
                "direction",
                "q",
                "status",
                "managerId",
                "regionId",
                "objectType",
                "createdFrom",
                "createdTo"
            })
    void cursorCannotBeReusedWithDifferentListParameters(String field) throws Exception {
        add("Перший", manager, region);
        add("Другий", manager, region);
        var parameters = new HashMap<>(Map.of("limit", "1"));
        parameters.put("cursor", page(director, parameters).path("nextCursor").asText());
        parameters.put(
                field,
                switch (field) {
                    case "limit" -> "2";
                    case "sort" -> "name";
                    case "direction" -> "asc";
                    case "managerId" -> manager.toString();
                    case "regionId" -> region.toString();
                    case "objectType" -> "HANGAR";
                    case "status" -> "FOUND";
                    case "createdFrom", "createdTo" -> "2026-01-15";
                    default -> "Перший";
                });
        request(director, parameters)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("cursor"));
    }

    @Test
    void cursorIsOpaqueTamperProofAndBoundToCurrentUserAndRegion() throws Exception {
        add("Перший", manager, region);
        add("Другий", colleague, region);
        String token = page(manager, Map.of("limit", "1")).path("nextCursor").asText();
        assertThat(token).doesNotContain("2026", manager.toString(), region.toString(), "FOUND");
        request(colleague, Map.of("limit", "1", "cursor", token)).andExpect(status().isUnprocessableEntity());
        request(manager, Map.of("limit", "1", "cursor", "A" + token.substring(1, token.length() - 2)))
                .andExpect(status().isUnprocessableEntity());
        UserRegionTestData.replace(jdbc, manager, otherRegion);
        request(manager, Map.of("limit", "1", "cursor", token)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void newObjectsDoNotShiftPagesAndDeletingBoundaryDoesNotInvalidateCursor() throws Exception {
        UUID first = add("Перший", manager, region, "2026-01-03T12:00:00Z", "FOUND");
        add("Другий", manager, region, "2026-01-02T12:00:00Z", "FOUND");
        String cursor = page(director, Map.of("limit", "1")).path("nextCursor").asText();
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id=?", first));
        add("Новий", manager, region, "2099-01-01T00:00:00Z", "FOUND");
        assertThat(names(page(director, Map.of("limit", "1", "cursor", cursor))))
                .containsExactly("Другий");
        assertThat(page(director, Map.of("managerId", UUID.randomUUID().toString()))
                        .path("nextCursor")
                        .isNull())
                .isTrue();
    }

    @Test
    void issuedCursorIsRejectedAfterDatabaseRoleChange() throws Exception {
        add("Перший", manager, region);
        add("Другий", colleague, region);
        String cursor = page(manager, Map.of("limit", "1")).path("nextCursor").asText();
        jdbc.update(
                "update users set role_id=(select id from roles where code='CONTRACT_MANAGER') where id=?", manager);
        request(manager, Map.of("limit", "1", "cursor", cursor)).andExpect(status().isUnprocessableEntity());
        assertThat(fields(page(manager, Map.of()).path("content").get(0)))
                .containsExactlyInAnyOrder("name", "entrance", "technicalSpecifications", "technicalDescription");
    }
}
