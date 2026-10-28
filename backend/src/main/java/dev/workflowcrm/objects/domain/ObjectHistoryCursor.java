package dev.workflowcrm.objects.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ObjectHistoryCursor {
    private ObjectHistoryCursor() {}

    static String encode(UUID object, long sequence) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((object + ":" + sequence).getBytes(StandardCharsets.US_ASCII));
    }

    static long decode(UUID object, String cursor) {
        if (cursor == null) return 0;
        try {
            if (cursor.isBlank() || cursor.length() > 128) throw new IllegalArgumentException();
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || !object.toString().equals(parts[0])) throw new IllegalArgumentException();
            long sequence = Long.parseLong(parts[1]);
            if (sequence <= 0 || !encode(object, sequence).equals(cursor)) throw new IllegalArgumentException();
            return sequence;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid history cursor");
        }
    }
}
