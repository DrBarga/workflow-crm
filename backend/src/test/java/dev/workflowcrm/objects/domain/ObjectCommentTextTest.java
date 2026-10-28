package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCommentTextTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t\r", "\u00A0", "\u200B\uFEFF"})
    void emptyOrInvisibleTextIsRejected(String value) {
        assertThatThrownBy(() -> ObjectCommentText.require(value)).isInstanceOf(UnprocessableInputException.class);
    }

    @Test
    void unicodeLimitCountsCharactersAndPreservesFormatting() {
        String value = "😀".repeat(1000);
        assertThat(ObjectCommentText.require(value)).isEqualTo(value);
        assertThat(ObjectCommentText.require("  Рядок\nДругий рядок  ")).isEqualTo("  Рядок\nДругий рядок  ");
        assertThatThrownBy(() -> ObjectCommentText.require(value + "x"))
                .hasMessageContaining("Request validation failed");
    }

    @Test
    void malformedUnicodeAndNullBytesAreRejected() {
        for (String value : new String[] {"a\u0000b", "\uD800", "\uDC00", "\uD800x"})
            assertThatThrownBy(() -> ObjectCommentText.require(value)).isInstanceOf(UnprocessableInputException.class);
    }
}
