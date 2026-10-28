package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectListUnicodeCursorIT extends ObjectListTestSupport {
    @Test
    void maximumLengthUnicodeNameFitsCursorAndContinuesCorrectly() throws Exception {
        String first = "界".repeat(500), second = "語".repeat(500);
        add(first, manager, region);
        add(second, manager, region);
        var parameters = new HashMap<>(Map.of("sort", "name", "direction", "asc", "limit", "1"));
        var initial = page(director, parameters);
        String cursor = initial.path("nextCursor").asText();
        assertThat(cursor.length()).isGreaterThan(2048).isLessThanOrEqualTo(4096);
        parameters.put("cursor", cursor);
        var next = page(director, parameters);
        assertThat(names(initial)).doesNotContainAnyElementsOf(names(next));
        assertThat(next.path("hasMore").asBoolean()).isFalse();
        request(director, Map.of("cursor", "a".repeat(4097))).andExpect(status().isUnprocessableEntity());
    }
}
