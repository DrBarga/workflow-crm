package dev.workflowcrm.objects.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class ObjectRequestBlocksTest {
    @Test
    void fingerprintsNormalizedBlockValuesAndPreservesHashForAbsentOrNullBlocks() throws Exception {
        var json = new ObjectMapper();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var parser = new ObjectRequestParser(json, validation.getValidator());
            var body = (ObjectNode)
                    json.readTree(
                            """
                    {"address":"Street 10","entrance":{"longitude":30.5234,"latitude":50.4501},"objectArea":100}
                    """);
            String original = parser.hash(parser.parse(body));
            body.putNull("technicalSpecifications");
            body.putNull("commercialTerms");
            assertThat(parser.hash(parser.parse(body))).isEqualTo(original);
            body.set("technicalSpecifications", json.readTree("{\"connectedPower\":20.00,\"buildingArea\":120.50}"));
            body.set("commercialTerms", json.readTree("{\"fixedRentAmount\":45000.2500,\"leaseTerms\":\"  Lease  \"}"));
            String expanded = parser.hash(parser.parse(body));
            assertThat(expanded).isNotEqualTo(original);
            body.set("technicalSpecifications", json.readTree("{\"buildingArea\":120.5,\"connectedPower\":20}"));
            body.set("commercialTerms", json.readTree("{\"leaseTerms\":\"Lease\",\"fixedRentAmount\":45000.25}"));
            body.put("confirmDuplicates", true);
            assertThat(parser.hash(parser.parse(body))).isEqualTo(expanded);
            body.set("commercialTerms", json.readTree("{\"leaseTerms\":\"Lease\",\"fixedRentAmount\":45000.26}"));
            assertThat(parser.hash(parser.parse(body))).isNotEqualTo(expanded);
        }
    }
}
