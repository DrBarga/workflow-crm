package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectHistoryPaginationIT extends ObjectHistoryTestSupport {
    @Test
    void emptyHistoryHasNoContinuation() throws Exception {
        var page = history(manager, add("Порожня історія", manager, region), Map.of());
        assertThat(page.path("items")).isEmpty();
        assertThat(page.path("hasMore").asBoolean()).isFalse();
        assertThat(page.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void cursorPagesHandleEqualTimestampsAndAppendedEventsWithoutDuplicates() throws Exception {
        UUID object = add("Пагінація", manager, region);
        for (int i = 0; i < 5; i++) entry(object, manager, "name", null, Integer.toString(i));
        var seen = new ArrayList<String>();
        var page = history(manager, object, Map.of("limit", "2"));
        page.path("items").forEach(e -> seen.add(e.path("newValue").asText()));
        entry(object, null, "STATUS", "FOUND", "IN_PROGRESS");
        while (page.path("hasMore").asBoolean()) {
            page = history(
                    manager,
                    object,
                    Map.of("limit", "2", "cursor", page.path("nextCursor").asText()));
            page.path("items").forEach(e -> seen.add(e.path("newValue").asText()));
        }
        assertThat(seen).containsExactly("0", "1", "2", "3", "4", "IN_PROGRESS");
        assertThat(page.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void largeHistoryReadsBoundedPagesThroughIndex() throws Exception {
        UUID object = add("Велика історія", manager, region);
        jdbc.update(
                """
            insert into object_history(object_id,actor_type,field_name,new_value,object_version)
            select ?,'SYSTEM','STATUS',n::text,0 from generate_series(1,50000) n
            """,
                object);
        var first = history(manager, object, Map.of("limit", "100"));
        assertThat(first.path("items").size()).isEqualTo(100);
        assertThat(first.path("items").get(99).path("newValue").asText()).isEqualTo("100");
        var next = history(
                manager,
                object,
                Map.of("limit", "1", "cursor", first.path("nextCursor").asText()));
        assertThat(next.path("items").get(0).path("newValue").asText()).isEqualTo("101");
        jdbc.execute("analyze object_history");
        String plan = String.join(
                "\n",
                jdbc.queryForList(
                        "explain select * from object_history where object_id=? and sequence>100 order by sequence limit 101",
                        String.class,
                        object));
        assertThat(plan).contains("Index").contains("Limit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "2147483647"})
    void invalidLimitIs422(String limit) throws Exception {
        historyRequest(manager, add("Ліміт", manager, region), Map.of("limit", limit))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad", "!!!!", "IA"})
    void malformedCursorIs422(String cursor) throws Exception {
        historyRequest(manager, add("Курсор", manager, region), Map.of("cursor", cursor))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cursorCannotBeUsedForAnotherObject() throws Exception {
        UUID first = add("Перший", manager, region), second = add("Другий", manager, region);
        entry(first, manager, "name", "1", "2");
        entry(first, manager, "name", "2", "3");
        String cursor =
                history(manager, first, Map.of("limit", "1")).path("nextCursor").asText();
        historyRequest(manager, second, Map.of("cursor", cursor)).andExpect(status().isUnprocessableEntity());
    }
}
