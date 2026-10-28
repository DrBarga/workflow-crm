package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectCommentPaginationIT extends ObjectCommentTestSupport {
    @Test
    void snapshotPagesSurviveDeletedAnchorAndEditsAndExcludeNewComments() throws Exception {
        UUID object = add("Pages", manager, region);
        var first = comment(manager, object, "first");
        var anchor = comment(manager, object, "anchor");
        var third = comment(manager, object, "third");
        var fourth = comment(manager, object, "fourth");
        var page = comments(manager, object, Map.of("limit", "2"));
        assertThat(ids(page)).containsExactly(id(first), id(anchor));
        assertThat(page.path("hasMore").asBoolean()).isTrue();
        String cursor = page.path("nextCursor").asText();
        removeComment(manager, object, id(anchor)).andExpect(status().isNoContent());
        patchComment(manager, object, id(third), Map.of("text", "changed", "version", 0))
                .andExpect(status().isOk());
        var appended = comment(manager, object, "appended");
        var next = comments(manager, object, Map.of("limit", "2", "cursor", cursor));
        assertThat(ids(next)).containsExactly(id(third), id(fourth));
        assertThat(next.path("items").get(0).path("text").asText()).isEqualTo("changed");
        assertThat(next.path("nextCursor").isNull()).isTrue();
        assertThat(next.path("hasMore").asBoolean()).isFalse();
        assertThat(ids(comments(manager, object, Map.of())))
                .containsExactly(id(first), id(third), id(fourth), id(appended));
    }

    @Test
    void cursorsCannotBeReusedAcrossObjectsAndRightsAreRecheckedOnEveryPage() throws Exception {
        UUID object = add("Source", manager, region), other = add("Other", manager, region);
        comment(manager, object, "a");
        comment(manager, object, "b");
        String cursor = comments(manager, object, Map.of("limit", "1"))
                .path("nextCursor")
                .asText();
        commentRequest(manager, other, Map.of("cursor", cursor)).andExpect(status().isUnprocessableEntity());
        for (String invalid : List.of("", "!", "a".repeat(193), cursor + "="))
            commentRequest(manager, object, Map.of("cursor", invalid)).andExpect(status().isUnprocessableEntity());
        for (String limit : List.of("0", "101", "-1"))
            commentRequest(manager, object, Map.of("limit", limit)).andExpect(status().isUnprocessableEntity());
        commentRequest(manager, object, Map.of("limit", "word")).andExpect(status().isBadRequest());
        jdbc.update("update objects set manager_id=? where id=?", colleague, object);
        commentRequest(manager, object, Map.of("cursor", cursor)).andExpect(status().isForbidden());
    }

    @Test
    void emptyAndLargePagesHaveStableBoundariesAndUseTheObjectSequenceIndex() throws Exception {
        UUID object = add("Large", manager, region);
        var empty = comments(manager, object, Map.of());
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("nextCursor").isNull()).isTrue();
        jdbc.update(
                "insert into object_comments(object_id,author_id,body) select ?,?,'comment '||n from generate_series(1,5000)n",
                object,
                manager);
        var seen = new HashSet<UUID>();
        String cursor = null;
        do {
            var page = comments(
                    manager,
                    object,
                    cursor == null ? Map.of("limit", "100") : Map.of("limit", "100", "cursor", cursor));
            for (UUID id : ids(page)) assertThat(seen.add(id)).isTrue();
            cursor = page.path("nextCursor").isNull()
                    ? null
                    : page.path("nextCursor").asText();
        } while (cursor != null);
        assertThat(seen).hasSize(5000);
        assertThat(jdbc.queryForList(
                        "select indexdef from pg_indexes where indexname='ix_object_comments_page'", String.class))
                .singleElement()
                .asString()
                .contains("(object_id, sequence)");
    }

    private List<UUID> ids(com.fasterxml.jackson.databind.JsonNode page) {
        var ids = new ArrayList<UUID>();
        page.path("items").forEach(item -> ids.add(id(item)));
        return ids;
    }
}
