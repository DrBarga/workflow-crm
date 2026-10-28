package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCommentHttpIT extends ObjectCommentTestSupport {
    @Test
    void createdCommentContainsOnlyPublicAuthorTextTimeAndServerMetadata() throws Exception {
        UUID object = add("Metadata", manager, region);
        var response = postComment(manager, object, Map.of("text", "  Текст\n<script>alert('x')</script>  "))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        var created = json.readTree(response.getContentAsByteArray());
        assertThat(fields(created.path("author"))).containsExactlyInAnyOrder("id", "name");
        assertThat(created.path("author").path("name").asText()).isEqualTo("Object Test User");
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(created.path("edited").asBoolean()).isFalse();
        assertThat(created.path("editedAt").isNull()).isTrue();
        assertThat(created.path("version").asLong()).isZero();
        assertThat(created.path("text").asText()).isEqualTo("  Текст\n<script>alert('x')</script>  ");
        assertThat(response.getHeader("Location")).isEqualTo("/api/objects/" + object + "/comments/" + id(created));
    }

    @Test
    void editMarksChangedTextAndSameTextDoesNotInventAnEdit() throws Exception {
        UUID object = add("Edit", manager, region);
        var created = comment(manager, object, "first");
        patchComment(manager, object, id(created), Map.of("text", "first", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(false));
        var edited = json.readTree(patchComment(manager, object, id(created), Map.of("text", "second", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editLabel").value("відредаговано"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(edited.path("version").asLong()).isEqualTo(1);
        assertThat(edited.path("editedAt").asText()).isNotBlank();
        assertThat(edited.path("createdAt")).isEqualTo(created.path("createdAt"));
        assertThat(edited.path("author")).isEqualTo(created.path("author"));
        patchComment(manager, object, id(created), Map.of("text", "stale", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        removeComment(manager, object, id(created)).andExpect(status().isNoContent());
        removeComment(manager, object, id(created)).andExpect(status().isNotFound());
        assertThat(comments(manager, object, Map.of()).path("items").size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u00A0", "\u200B"})
    void blankCommentIs422(String text) throws Exception {
        UUID object = add("Blank", manager, region);
        postComment(manager, object, Map.of("text", text))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("text"));
        var comment = comment(manager, object, "keep");
        patchComment(manager, object, id(comment), Map.of("text", text, "version", 0))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unicodeBoundaryAndLimitAreConsistentWithDatabase() throws Exception {
        UUID object = add("Unicode", manager, region);
        comment(manager, object, "😀".repeat(1000));
        postComment(manager, object, Map.of("text", "😀".repeat(1001)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].message").value("Comment cannot exceed 1000 characters"));
        postComment(manager, object, Map.of("text", "a\u0000b")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownAndProtectedFieldsAndWrongTypesAreRejected() throws Exception {
        UUID object = add("Validation", manager, region);
        for (Object body : List.of(
                Map.of(),
                Map.of("text", 123),
                Map.of("text", "x", "authorId", lead.toString()),
                Map.of("text", "x", "createdAt", "2026-01-01"),
                List.of("x"))) postComment(manager, object, body).andExpect(status().isUnprocessableEntity());
        var created = comment(manager, object, "keep");
        for (Object body : List.of(
                Map.of("text", "x"),
                Map.of("text", "x", "version", "0"),
                Map.of("text", "x", "version", -1),
                Map.of("text", "x", "version", 0.5),
                Map.of("text", "x", "version", 0, "authorId", lead.toString())))
            patchComment(manager, object, id(created), body).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_comments")).isEqualTo(1);
    }

    @Test
    void commentIdCannotBeUsedOnAnotherObject() throws Exception {
        UUID object = add("First", manager, region), other = add("Second", manager, region);
        var created = comment(manager, object, "keep");
        patchComment(manager, other, id(created), Map.of("text", "x", "version", 0))
                .andExpect(status().isNotFound());
        removeComment(manager, other, id(created)).andExpect(status().isNotFound());
        assertThat(count("object_comments")).isEqualTo(1);
    }

    @Test
    void missingAuthenticationCsrfMalformedJsonAndUuidHaveCorrectStatuses() throws Exception {
        UUID object = add("Transport", manager, region);
        String path = "/api/objects/" + object + "/comments";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path)
                        .cookie(cookie(manager))
                        .contentType("application/json")
                        .content("{\"text\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path)
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/objects/not-a-uuid/comments").cookie(cookie(manager)))
                .andExpect(status().isBadRequest());
    }
}
