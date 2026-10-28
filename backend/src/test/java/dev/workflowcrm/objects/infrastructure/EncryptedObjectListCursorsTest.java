package dev.workflowcrm.objects.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EncryptedObjectListCursorsTest {
    final Instant now = Instant.parse("2026-10-06T12:00:00Z");
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final ObjectListFilter filter = new ObjectListFilter(
            null, null, null, null, null, null, null, ObjectListFilter.Sort.NAME, ObjectListFilter.Direction.ASC, 50);
    final ObjectListScope scope = new ObjectListScope(UUID.randomUUID(), "CEO", List.of());

    EncryptedObjectListCursors codec(Instant time, String secret) throws Exception {
        return new EncryptedObjectListCursors(json, Clock.fixed(time, ZoneOffset.UTC), secret);
    }

    @Test
    void roundTripKeepsNullBoundaryButEncryptsPrivateSortValueWithRandomNonce() throws Exception {
        var codec = codec(now, "test-list-secret-at-least-32-characters");
        var position = new ObjectListPosition(UUID.randomUUID(), "Приватне значення", now, now.plusSeconds(900));
        String first = codec.write(position, filter, scope), second = codec.write(position, filter, scope);
        assertThat(first).isNotEqualTo(second);
        assertThat(codec.read(first, filter, scope)).isEqualTo(position);
        var empty = new ObjectListPosition(position.id(), null, now, position.expiresAt());
        assertThat(codec.read(codec.write(empty, filter, scope), filter, scope)).isEqualTo(empty);
    }

    @Test
    void expiryAndKeyRotationRequireRestartingPagination() throws Exception {
        var codec = codec(now, "test-list-secret-at-least-32-characters");
        var position = new ObjectListPosition(UUID.randomUUID(), null, now, now.plusSeconds(900));
        String token = codec.write(position, filter, scope);
        assertThatThrownBy(() -> codec(now.plusSeconds(900), "test-list-secret-at-least-32-characters")
                        .read(token, filter, scope))
                .isInstanceOf(UnprocessableInputException.class);
        assertThatThrownBy(() -> codec(now, "different-list-secret-at-least-32-characters")
                        .read(token, filter, scope))
                .isInstanceOf(UnprocessableInputException.class);
        for (String invalid : List.of("", "abc", "a".repeat(ObjectListCursors.MAX_LENGTH + 1), "invalid+token"))
            assertThatThrownBy(() -> codec.read(invalid, filter, scope))
                    .isInstanceOf(UnprocessableInputException.class);
    }
}
