package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class JacksonObjectEditFingerprint implements ObjectEditFingerprint {
    private final ObjectMapper json;

    JacksonObjectEditFingerprint(ObjectMapper json) {
        this.json = json;
    }

    public String token(UUID actor, ObjectPatchCommand command, String taskToken) {
        record Preview(UUID actorId, long version, Set<String> fields, ObjectEditValues values, String taskToken) {}
        try {
            var value =
                    new Preview(actor, command.version(), new TreeSet<>(command.fields()), command.values(), taskToken);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot fingerprint object edit", e);
        }
    }
}
