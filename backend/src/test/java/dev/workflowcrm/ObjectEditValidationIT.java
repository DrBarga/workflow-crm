package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditValidationIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "\"0\"", "null", "9223372036854775808"})
    void versionMustBeNonnegativeInteger(String version) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var value = json.readValue("{\"version\":" + version + ",\"name\":\"Нова назва\"}", Map.class);
        patch(manager, object, value).andExpect(status().isUnprocessableEntity());
        assertThat(version(object)).isZero();
    }

    @Test
    void missingVersionEmptyPatchAndUnknownNestedFieldsAreRejected() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(manager, object, Map.of("name", "Нова назва")).andExpect(status().isUnprocessableEntity());
        patch(manager, object, Map.of("version", 0)).andExpect(status().isUnprocessableEntity());
        patch(manager, object, change(object, "technicalSpecifications", Map.of("geoM", 1)))
                .andExpect(status().isUnprocessableEntity());
        patch(manager, object, change(object, "commercialTerms", Map.of("fixedRentAmount", "20")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidAreasNamesTypesAndDatesHaveFieldErrors() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        for (var body : List.of(
                change(object, "name", " "),
                change(object, "objectArea", 0),
                change(object, "objectType", "UNKNOWN"),
                change(object, "openingPlannedDate", "2026-02-30"),
                change(object, "technicalSpecifications", Map.of("connectedPower", -1))))
            patch(manager, object, body).andExpect(status().isUnprocessableEntity());
        var body = change(object, "objectArea", null);
        body.put("landArea", null);
        patch(manager, object, body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void partialPatchPreservesOtherFieldsAndOptionalFieldsCanBeCleared() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(manager, object, change(object, "technicalDescription", null)).andExpect(status().isOk());
        var card = card(manager, object);
        assertThat(card.path("address").asText()).isEqualTo("Приватна адреса");
        assertThat(card.path("objectArea").decimalValue()).isEqualByComparingTo("120");
        assertThat(card.path("commercialDescription").asText()).isEqualTo("Умови");
        assertThat(card.path("technicalDescription").isNull()).isTrue();
    }

    @Test
    void legacyContractTypeSurvivesUnrelatedPatch() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        jdbc.update("update objects set contract_type='Історичний договір' where id=?", object);
        patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select contract_type from objects where id=?", String.class, object))
                .isEqualTo("Історичний договір");
    }

    @Test
    void ordinaryFieldHistoryContainsOldNewAndFinalVersion() throws Exception {
        UUID object = add("Початкова назва", manager, region);
        var body = change(object, "name", "Нова назва");
        body.put("technicalSpecifications", Map.of("connectedPower", 40));
        body.put("commercialTerms", Map.of("leaseTerms", "Нові умови", "fixedRentAmount", 5000));
        patch(manager, object, body).andExpect(status().isOk());
        var row = jdbc.queryForMap(
                "select previous_value,new_value,object_version from object_history where object_id=? and field_name='name'",
                object);
        assertThat(row.get("previous_value")).isEqualTo("\"Початкова назва\"");
        assertThat(row.get("new_value")).isEqualTo("\"Нова назва\"");
        assertThat(row.get("object_version")).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_audit where object_id=? and event_type='FIELDS_CHANGED'",
                        Integer.class,
                        object))
                .isEqualTo(1);
    }
}
