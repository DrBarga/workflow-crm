package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IcDecisionRefusalCommentIT extends IcDecisionTestSupport {
    @Test
    void rejectsMissingCommentWithoutChangingObjectOrTasks() throws Exception {
        var body = decision("REFUSED");
        body.remove("refusalComment");
        assertRejected(body);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u202f", "\u2003", " \u00a0\u202f\u2003 "})
    void rejectsNullEmptyAndWhitespaceComments(String comment) throws Exception {
        var body = decision("REFUSED");
        body.put("refusalComment", comment);
        assertRejected(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"а", "😀"})
    void rejectsFiftyOneCharactersWithoutChangingObjectOrTasks(String character) throws Exception {
        var body = decision("REFUSED");
        body.put("refusalComment", character.repeat(51));
        assertRejected(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"а", "😀"})
    void acceptsFiftyCharactersAndStoresTrimmedComment(String character) throws Exception {
        var body = decision("REFUSED");
        var comment = character.repeat(50);
        body.put("refusalComment", "\u00a0 " + comment + " \u202f");
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusalComment").value(comment));
        assertThat(state()).isEqualTo("REFUSED");
        assertThat(jdbc.queryForObject(
                        "select investment_committee_refusal_comment from objects where id=?", String.class, object))
                .isEqualTo(comment);
        assertThat(decisionHistoryCount()).isEqualTo(2);
    }

    private void assertRejected(java.util.Map<String, Object> body) throws Exception {
        task("IN_PROGRESS");
        var beforeObject =
                jdbc.queryForObject("select to_jsonb(o)::text from objects o where id=?", String.class, object);
        var beforeTasks = taskSnapshot();
        decide(lead, body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("refusalComment"));
        assertThat(jdbc.queryForObject("select to_jsonb(o)::text from objects o where id=?", String.class, object))
                .isEqualTo(beforeObject);
        assertThat(taskSnapshot()).isEqualTo(beforeTasks);
        assertThat(count("object_history")).isZero();
        assertThat(count("task_history")).isZero();
    }
}
