package dev.workflowcrm.objects.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectRequestFingerprintTest {
    @Test
    void absentContactReferencePreservesPublishedFingerprint() throws Exception {
        var json = new ObjectMapper();
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var parser = new ObjectRequestParser(json, validators.getValidator());
            var request = parser.parse(
                    json.readTree(
                            """
                    {"name":"Object","entrance":{"longitude":30.5,"latitude":50.4},"objectArea":100}
                    """));
            var legacy = new LegacyRequest(
                    request.name(),
                    request.objectType(),
                    request.address(),
                    request.regionId(),
                    request.entrance(),
                    request.objectArea(),
                    request.landArea(),
                    request.contact(),
                    request.managerId());
            var hash = HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(legacy)));
            assertThat(parser.hash(request)).isEqualTo(hash);
            var reference = parser.parse(
                    json.readTree(
                            """
                    {"name":"Object","entrance":{"longitude":30.5,"latitude":50.4},"objectArea":100,
                     "contactId":"00000000-0000-0000-0000-000000000001"}
                    """));
            assertThat(parser.hash(reference)).isNotEqualTo(hash);
        }
    }

    private record LegacyRequest(
            String name,
            String objectType,
            String address,
            UUID regionId,
            CreateObjectRequest.Entrance entrance,
            BigDecimal objectArea,
            BigDecimal landArea,
            CreateObjectRequest.Contact contact,
            UUID managerId) {}
}
