package dev.workflowcrm.files.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ObjectFileCursor {
    record Position(long after, long snapshot) {}

    private ObjectFileCursor() {}

    static String encode(UUID object, long after, long snapshot) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((object + ":" + after + ":" + snapshot).getBytes(StandardCharsets.US_ASCII));
    }

    static Position decode(UUID object, String cursor) {
        try {
            if (cursor == null || cursor.isBlank() || cursor.length() > 192) throw new IllegalArgumentException();
            var values = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII).split(":", -1);
            if (values.length != 3 || !object.toString().equals(values[0])) throw new IllegalArgumentException();
            long after = Long.parseLong(values[1]), snapshot = Long.parseLong(values[2]);
            if (after <= 0
                    || snapshot < after
                    || !encode(object, after, snapshot).equals(cursor)) throw new IllegalArgumentException();
            return new Position(after, snapshot);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid file cursor");
        }
    }
}
