package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ObjectCommentCursorTest {
    @Test
    void cursorRoundTripsAndIsBoundToObject() {
        var object = UUID.randomUUID();
        var cursor = ObjectCommentCursor.encode(object, 12, 50);
        assertThat(ObjectCommentCursor.decode(object, cursor)).isEqualTo(new ObjectCommentCursor.Position(12, 50));
        assertThatThrownBy(() -> ObjectCommentCursor.decode(UUID.randomUUID(), cursor))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void invalidAndNonCanonicalPositionsAreRejected() {
        var object = UUID.randomUUID();
        for (String cursor : new String[] {
            "",
            "%",
            "a".repeat(193),
            ObjectCommentCursor.encode(object, 0, 2),
            ObjectCommentCursor.encode(object, 10, 5),
            ObjectCommentCursor.encode(object, 1, 2) + "="
        })
            assertThatThrownBy(() -> ObjectCommentCursor.decode(object, cursor))
                    .isInstanceOf(ResponseStatusException.class);
    }
}
