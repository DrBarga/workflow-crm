package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectListCommentIT extends ObjectListTestSupport {
    private static final String COMMENT = "К".repeat(200);

    @ParameterizedTest
    @ValueSource(strings = {"manager", "lead", "director", "ceo", "admin", "contract"})
    void commentFollowsFullVisibilityForEveryRole(String role) throws Exception {
        addCommented("A", manager, region);
        addCommented("B", colleague, region);
        addCommented("C", outsider, otherRegion);
        var result = page(actor(role), Map.of("sort", "name", "direction", "asc"));
        assertThat(names(result))
                .containsExactlyElementsOf(role.equals("manager") ? List.of("A", "B") : List.of("A", "B", "C"));
        for (var row : result.path("content")) {
            String name = row.path("name").asText();
            boolean full =
                    switch (role) {
                        case "manager" -> name.equals("A");
                        case "lead" -> !name.equals("C");
                        case "contract" -> false;
                        default -> true;
                    };
            if (full) assertThat(row.path("shortComment").asText()).isEqualTo(COMMENT);
            else assertRestricted(row, role.equals("contract"));
        }
    }

    @Test
    void missingCommentIsExplicitNullOnlyInFullRows() throws Exception {
        add("A", manager, region);
        add("B", outsider, otherRegion);
        var rows = page(lead, Map.of("sort", "name", "direction", "asc")).path("content");
        assertThat(rows.get(0).has("shortComment")).isTrue();
        assertThat(rows.get(0).path("shortComment").isNull()).isTrue();
        assertRestricted(rows.get(1), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void cursorPagesKeepCommentsOutOfForeignPins(String direction) throws Exception {
        addCommented("A", manager, region);
        addCommented("B", outsider, otherRegion);
        addCommented("C", colleague, region);
        addCommented("D", outsider, otherRegion);
        var parameters = new HashMap<>(Map.of("sort", "name", "direction", direction, "limit", "1"));
        var seen = new ArrayList<String>();
        for (int i = 0; i < 4; i++) {
            var result = page(lead, parameters);
            assertThat(result.path("content")).hasSize(1);
            var row = result.path("content").get(0);
            String name = row.path("name").asText();
            seen.add(name);
            if (Set.of("A", "C").contains(name))
                assertThat(row.path("shortComment").asText()).isEqualTo(COMMENT);
            else assertRestricted(row, false);
            assertThat(result.path("hasMore").asBoolean()).isEqualTo(i < 3);
            if (i < 3) parameters.put("cursor", result.path("nextCursor").asText());
            else assertThat(result.path("nextCursor").isNull()).isTrue();
        }
        assertThat(seen)
                .containsExactlyElementsOf(
                        direction.equals("asc") ? List.of("A", "B", "C", "D") : List.of("D", "C", "B", "A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manager", "lead", "director", "contract"})
    void commentDoesNotExtendNameAndAddressSearch(String role) throws Exception {
        addCommented("A", manager, region);
        addCommented("B", colleague, region);
        addCommented("C", outsider, otherRegion);
        assertThat(page(actor(role), Map.of("q", COMMENT)).path("content")).isEmpty();
    }

    private void addCommented(String name, UUID owner, UUID area) {
        var id = add(name, owner, area);
        jdbc.update("update objects set short_comment=? where id=?", COMMENT, id);
    }

    private UUID actor(String role) {
        return switch (role) {
            case "manager" -> manager;
            case "lead" -> lead;
            case "director" -> director;
            case "ceo" -> ceo;
            case "admin" -> admin;
            default -> contract;
        };
    }

    private void assertRestricted(JsonNode row, boolean technical) {
        assertThat(row.has("shortComment")).isFalse();
        if (technical)
            assertThat(fields(row))
                    .containsExactlyInAnyOrder("name", "entrance", "technicalSpecifications", "technicalDescription");
        else assertThat(fields(row)).containsExactlyInAnyOrder("name", "entrance");
    }
}
