package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCreationValidationIT extends ObjectApiTestSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "status",
                "authorId",
                "createdAt",
                "commercialTerms",
                "technicalSpecifications",
                "contractType",
                "googleMapsUrl",
                "unexpected"
            })
    void rejectsUnacceptedAndProtectedFields(String field) throws Exception {
        var body = body();
        body.put(field, "test");
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value(field));
        assertThat(count("objects")).isZero();
    }

    @Test
    void rejectsUnknownNestedFieldsAndWrongTypes() throws Exception {
        var body = body();
        body.put("entrance", Map.of("longitude", 30.52, "latitude", 50.45, "status", "FOUND"));
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("entrance.status"));
        body = body();
        body.put("objectArea", "120");
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("objectArea"));
    }

    @Test
    void reportsMissingCoordinatesAndAreaTogether() throws Exception {
        var body = body();
        body.remove("entrance");
        body.remove("objectArea");
        var response = create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse();
        assertThat(response.getContentAsString()).contains("entrance", "objectArea");
        assertThat(count("objects")).isZero();
    }

    @Test
    void rejectsNegativeAreasExcessPrecisionAndIncompleteContact() throws Exception {
        var body = body();
        body.put("objectArea", -1);
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        body = body();
        body.put("entrance", Map.of("longitude", 30.123456789, "latitude", 50.45));
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        body = body();
        body.put("contact", Map.of("name", "Owner", "role", "Owner"));
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsForeignCoordinatesEvenInsideUkraineBoundingRectangle() throws Exception {
        var body = body();
        body.put("entrance", Map.of("longitude", 28.8638, "latitude", 47.0105));
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("entrance"));
        assertThat(count("objects")).isZero();
    }

    @Test
    void rejectsUnknownInactiveTypeAndMissingRegionMapping() throws Exception {
        var body = body();
        body.put("objectType", "UNKNOWN");
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        jdbc.update("update object_types set is_active=false where code='BUILT_IN_PREMISES'");
        try {
            create(manager, key(), body()).andExpect(status().isUnprocessableEntity());
        } finally {
            jdbc.update("update object_types set is_active=true where code='BUILT_IN_PREMISES'");
        }
        body = body();
        body.remove("regionId");
        jdbc.update("update regions set code='CUSTOM',name='Custom' where id=?", region);
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("regionId"));
    }
}
