package dev.workflowcrm.objects.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ObjectRequestParserTest {
    @Test
    void confirmationDoesNotChangeFingerprintOrPublishedRequestHash() throws Exception {
        var json = new ObjectMapper();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var parser = new ObjectRequestParser(json, validation.getValidator());
            String legacy =
                    """
                    {"name":"Retail","objectType":null,"address":"Street 10","regionId":null,
                     "entrance":{"longitude":30.5234,"latitude":50.4501},"objectArea":1.2E+2,
                     "landArea":null,"contact":null,"managerId":null}
                    """;
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(legacy);
            var request = parser.parse(body);
            String legacyJson = "{\"name\":\"Retail\",\"objectType\":null,\"address\":\"Street 10\",\"regionId\":null,"
                    + "\"entrance\":{\"longitude\":30.5234,\"latitude\":50.4501},\"objectArea\":1.2E+2,\"landArea\":null,\"contact\":null,\"managerId\":null}";
            String expected = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(legacyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(json.writeValueAsString(request)).doesNotContain("confirmDuplicates");
            assertThat(parser.hash(request)).isEqualTo(expected);
            body.put("confirmDuplicates", true);
            var confirmed = parser.parse(body);
            assertThat(confirmed.confirmDuplicates()).isTrue();
            assertThat(parser.hash(confirmed)).isEqualTo(parser.hash(request));
            body.put("confirmDuplicates", false);
            assertThat(parser.hash(parser.parse(body))).isEqualTo(expected);
        }
    }
}
