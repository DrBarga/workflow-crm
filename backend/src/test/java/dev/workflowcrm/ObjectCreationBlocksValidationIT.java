package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ObjectCreationBlocksValidationIT extends ObjectApiTestSupport {
    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidNestedFieldsWithPathsAndNoPartialWrites(String block, String field, Object value)
            throws Exception {
        var body = body();
        body.put(block, Map.of(field, value));
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value(block + "." + field));
        assertThat(count("objects")).isZero();
        assertThat(count("object_creation_requests")).isZero();
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("technicalSpecifications", "buildingArea", -1),
                Arguments.of("technicalSpecifications", "connectedPower", 1.001),
                Arguments.of("technicalSpecifications", "waterSupplyFlow", 1000000000000L),
                Arguments.of("technicalSpecifications", "floorSlabHeight", "3.2"),
                Arguments.of("technicalSpecifications", "powerConnectionCategory", "IV"),
                Arguments.of("technicalSpecifications", "powerConnectionCategory", 0),
                Arguments.of("technicalSpecifications", "sewageType", "OTHER"),
                Arguments.of("technicalSpecifications", "unexpected", 1),
                Arguments.of("commercialTerms", "fixedRentAmount", -0.01),
                Arguments.of("commercialTerms", "fixedRentAmount", "15"),
                Arguments.of("commercialTerms", "rentIndexationPercent", 100000),
                Arguments.of("commercialTerms", "turnoverPaymentPercent", 100.01),
                Arguments.of("commercialTerms", "counterpartyTaxStatus", "OTHER"),
                Arguments.of("commercialTerms", "constructionTermDays", -1),
                Arguments.of("commercialTerms", "constructionTermDays", 1.5),
                Arguments.of("commercialTerms", "contractDurationYears", 1000),
                Arguments.of("commercialTerms", "leaseTerms", "а".repeat(5001)),
                Arguments.of("commercialTerms", "leaseTerms", "<script>"),
                Arguments.of("commercialTerms", "unexpected", 1));
    }
}
