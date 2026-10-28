package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.objects.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectCommentTransactionIT extends ObjectCommentTestSupport {
    private long version(UUID object) {
        return jdbc.queryForObject("select version from objects where id=?", Long.class, object);
    }

    @Autowired
    ObjectCommentService service;

    @Autowired
    ObjectDeletionService deletions;

    @Autowired
    ObjectDeletionProcessor processor;

    @Test
    void simultaneousEditsPreserveTheWinnerAndReportCurrentCommentVersion() throws Exception {
        UUID object = add("Concurrent", manager, region);
        var created = comment(manager, object, "original");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<org.springframework.mock.web.MockHttpServletResponse> first = () -> {
                barrier.await();
                return patchComment(manager, object, id(created), Map.of("text", "first", "version", 0))
                        .andReturn()
                        .getResponse();
            };
            Callable<org.springframework.mock.web.MockHttpServletResponse> second = () -> {
                barrier.await();
                return patchComment(manager, object, id(created), Map.of("text", "second", "version", 0))
                        .andReturn()
                        .getResponse();
            };
            var f = pool.submit(first);
            var s = pool.submit(second);
            var responses = List.of(f.get(30, TimeUnit.SECONDS), s.get(30, TimeUnit.SECONDS));
            assertThat(responses.stream().map(r -> r.getStatus()).toList()).containsExactlyInAnyOrder(200, 409);
            var winner = json.readTree(responses.stream()
                    .filter(r -> r.getStatus() == 200)
                    .findFirst()
                    .orElseThrow()
                    .getContentAsByteArray());
            var conflict = json.readTree(responses.stream()
                    .filter(r -> r.getStatus() == 409)
                    .findFirst()
                    .orElseThrow()
                    .getContentAsByteArray());
            assertThat(conflict.path("currentVersion").asLong()).isEqualTo(1);
            assertThat(comments(manager, object, Map.of()).path("items").get(0).path("text"))
                    .isEqualTo(winner.path("text"));
        }
        assertThat(count("object_history")).isEqualTo(2);
        assertThat(version(object)).isZero();
    }

    @Test
    void everyMutationRecordsOldAndNewCommentInTheSameTransaction() throws Exception {
        UUID object = add("History", manager, region);
        var created = comment(manager, object, "old");
        patchComment(manager, object, id(created), Map.of("text", "new", "version", 0))
                .andExpect(status().isOk());
        removeComment(manager, object, id(created)).andExpect(status().isNoContent());
        var fields = jdbc.queryForList(
                "select field_name from object_history where object_id=? order by sequence", String.class, object);
        assertThat(fields).containsExactly("COMMENT_CREATED", "COMMENT_EDITED", "COMMENT_DELETED");
        var edit = jdbc.queryForMap(
                "select previous_value,new_value,author_id from object_history where field_name='COMMENT_EDITED'");
        assertThat(json.readTree(edit.get("previous_value").toString())
                        .path("text")
                        .asText())
                .isEqualTo("old");
        assertThat(json.readTree(edit.get("new_value").toString()).path("text").asText())
                .isEqualTo("new");
        assertThat(edit.get("author_id")).isEqualTo(manager);
        assertThat(version(object)).isZero();
    }

    @Test
    void historyFailureRollsBackCreateEditAndDeleteIncludingEditedMetadata() throws Exception {
        UUID object = add("Rollback", manager, region);
        var created = comment(manager, object, "keep");
        jdbc.execute(
                "alter table object_history add constraint test_comment_history check(field_name not like 'COMMENT_%') not valid");
        try {
            postComment(manager, object, Map.of("text", "new")).andExpect(status().isInternalServerError());
            patchComment(manager, object, id(created), Map.of("text", "changed", "version", 0))
                    .andExpect(status().isInternalServerError());
            removeComment(manager, object, id(created)).andExpect(status().isInternalServerError());
            var item = comments(manager, object, Map.of()).path("items").get(0);
            assertThat(item.path("text").asText()).isEqualTo("keep");
            assertThat(item.path("edited").asBoolean()).isFalse();
            assertThat(item.path("version").asLong()).isZero();
            assertThat(count("object_comments")).isEqualTo(1);
            assertThat(count("object_history")).isEqualTo(1);
        } finally {
            jdbc.execute("alter table object_history drop constraint test_comment_history");
        }
    }

    @Test
    void pendingDeletionFreezesCommentsAndCompletedDeletionKeepsThemOnlyInAudit() throws Exception {
        UUID object = add("Deletion", manager, region);
        var created = comment(manager, object, "audit text");
        patchComment(manager, object, id(created), Map.of("text", "edited audit text", "version", 0))
                .andExpect(status().isOk());
        deletions.request(admin, object);
        commentRequest(manager, object, Map.of()).andExpect(status().isConflict());
        postComment(manager, object, Map.of("text", "blocked")).andExpect(status().isConflict());
        patchComment(manager, object, id(created), Map.of("text", "blocked", "version", 1))
                .andExpect(status().isConflict());
        removeComment(manager, object, id(created)).andExpect(status().isConflict());
        assertThatThrownBy(() -> service.create(manager, object, "blocked"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(processor.processNext()).isTrue();
        assertThat(count("object_comments")).isZero();
        assertThat(count("object_history")).isZero();
        commentRequest(manager, object, Map.of()).andExpect(status().isNotFound());
        postComment(manager, object, Map.of("text", "gone")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select snapshot::text from object_deletion_audit", String.class))
                .contains("edited audit text", "COMMENT_EDITED", "edited_at");
    }
}
