package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ObjectCommentMentionValidationIT extends ObjectCommentMentionsTestSupport {
    static Stream<Object> invalidMentions() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                "not-an-array",
                Map.of(),
                List.of("not-an-object"),
                List.of(Map.of("userId", id, "offset", 0)),
                List.of(Map.of("userId", "bad", "offset", 0, "length", 2)),
                List.of(Map.of("userId", "1-1-1-1-1", "offset", 0, "length", 2)),
                List.of(Map.of("userId", id, "offset", "0", "length", 2)),
                List.of(Map.of("userId", id, "offset", 0.5, "length", 2)),
                List.of(Map.of("userId", id, "offset", Long.MAX_VALUE, "length", 2)),
                List.of(Map.of("userId", id, "offset", 0, "length", 2, "extra", true)),
                Collections.nCopies(501, Map.of("userId", id, "offset", 0, "length", 2)));
    }

    @ParameterizedTest
    @MethodSource("invalidMentions")
    void malformedMetadataIs422WithoutSideEffects(Object selected) throws Exception {
        UUID object = add("Проверка формата", manager, region);
        postComment(manager, object, Map.of("text", label(lead), "mentions", selected))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("mentions"));
        assertThat(count("object_comments")).isZero();
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void nullOverlappingOutOfBoundsAndMismatchedLabelsAre422() throws Exception {
        UUID object = add("Проверка диапазонов", manager, region);
        var nullBody = new LinkedHashMap<String, Object>();
        nullBody.put("text", label(lead));
        nullBody.put("mentions", null);
        postComment(manager, object, nullBody).andExpect(status().isUnprocessableEntity());
        for (var selected : List.of(
                List.of(mention(lead, 0), mention(lead, 0)),
                List.of(mention(lead, -1)),
                List.of(mention(lead, 1000)),
                List.of(Map.<String, Object>of("userId", lead.toString(), "offset", 0, "length", Integer.MAX_VALUE)),
                List.of(Map.<String, Object>of("userId", lead.toString(), "offset", 0, "length", 1))))
            postComment(manager, object, body(label(lead), selected)).andExpect(status().isUnprocessableEntity());
        postComment(manager, object, body("@Wrong name", List.of(mention(lead, 0))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("object_history")).isZero();
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void offsetsUseUtf16AndCommentLimitStillCountsUnicodeCodePoints() throws Exception {
        UUID object = add("Unicode упоминание", manager, region);
        String prefix = "😀 ";
        String name = label(lead);
        String text = prefix + name + "a".repeat(1000 - 2 - name.length());
        var created = json.readTree(postComment(manager, object, body(text, List.of(mention(lead, prefix.length()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(created.path("mentions").get(0).path("offset").asInt()).isEqualTo(3);
        postComment(manager, object, body(text + "a", List.of(mention(lead, prefix.length()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("text"));
        postComment(manager, object, body(prefix + name, List.of(mention(lead, 2))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(notifications(lead)).isEqualTo(1);
    }

    @Test
    void revokedRecipientIsRecheckedOnEditButCanBeRemovedWithoutNotifying() throws Exception {
        UUID object = add("Отзыв доступа", manager, region);
        var created = json.readTree(postComment(manager, object, body(label(lead), List.of(mention(lead, 0))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        UserRegionTestData.replace(jdbc, lead, otherRegion);
        var edit = body(label(lead) + " Проверить", List.of(mention(lead, 0)));
        edit.put("version", 0);
        patchComment(manager, object, id(created), edit).andExpect(status().isUnprocessableEntity());
        patchComment(manager, object, id(created), Map.of("text", label(lead), "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentions").isEmpty())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(notifications(lead)).isEqualTo(1);
    }
}
