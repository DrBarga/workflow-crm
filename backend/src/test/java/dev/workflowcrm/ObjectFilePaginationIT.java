package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectFilePaginationIT extends ObjectFileTestSupport {
    @Test
    void cursorContinuesAfterDeletedRowAndConcurrentUploadAppearsOnlyInNewSnapshot() throws Exception {
        UUID object = add("Page", manager, region);
        var first = uploaded(manager, object, "OTHER");
        var second = uploaded(manager, object, "OTHER");
        var third = uploaded(manager, object, "OTHER");
        var page = filePage(manager, object, Map.of("limit", "1"));
        assertThat(page.path("items").get(0).path("id")).isEqualTo(first.path("id"));
        var appended = uploaded(manager, object, "OTHER");
        deleteFile(manager, object, fileId(first)).andExpect(status().isNoContent());
        var next = filePage(
                manager,
                object,
                Map.of("limit", "1", "cursor", page.path("nextCursor").asText()));
        assertThat(next.path("items").get(0).path("id")).isEqualTo(second.path("id"));
        var last = filePage(
                manager, object, Map.of("cursor", next.path("nextCursor").asText()));
        assertThat(last.path("items").size()).isEqualTo(1);
        assertThat(last.path("items").get(0).path("id")).isEqualTo(third.path("id"));
        assertThat(last.path("hasMore").asBoolean()).isFalse();
        assertThat(filePage(manager, object, Map.of()).path("items").get(2).path("id"))
                .isEqualTo(appended.path("id"));
    }

    @Test
    void invalidLimitAndCursorAndForeignCursorAreRejectedAndRightsAreCheckedForEveryPage() throws Exception {
        UUID object = add("Cursor", manager, region), other = add("Other", manager, region);
        uploaded(manager, object, "OTHER");
        uploaded(manager, object, "OTHER");
        String cursor = filePage(manager, object, Map.of("limit", "1"))
                .path("nextCursor")
                .asText();
        for (String invalid : List.of("", "0", "bad", cursor + "="))
            fileList(manager, object, Map.of("cursor", invalid)).andExpect(status().isUnprocessableEntity());
        fileList(manager, other, Map.of("cursor", cursor)).andExpect(status().isUnprocessableEntity());
        for (String limit : List.of("0", "101", "-1"))
            fileList(manager, object, Map.of("limit", limit)).andExpect(status().isUnprocessableEntity());
        fileList(manager, object, Map.of("limit", "a")).andExpect(status().isBadRequest());
        jdbc.update("update objects set manager_id=? where id=?", colleague, object);
        fileList(manager, object, Map.of("cursor", cursor)).andExpect(status().isForbidden());
    }
}
