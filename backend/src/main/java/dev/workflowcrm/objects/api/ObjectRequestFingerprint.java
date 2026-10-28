package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.HexFormat;

final class ObjectRequestFingerprint {
    private ObjectRequestFingerprint() {}

    static String hash(CreateObjectRequest request, ObjectMapper json) {
        try {
            ObjectNode payload = json.valueToTree(request);
            if (request.contactId() == null) payload.remove("contactId");
            if (request.technicalSpecifications() == null) payload.remove("technicalSpecifications");
            if (request.commercialTerms() == null) payload.remove("commercialTerms");
            if (request.shortComment() == null) payload.remove("shortComment");
            if (request.dataSourceId() == null) payload.remove("dataSourceId");
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint object request", exception);
        }
    }
}
