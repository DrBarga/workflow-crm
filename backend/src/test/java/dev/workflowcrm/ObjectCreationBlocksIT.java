package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCreationBlocksIT extends ObjectApiTestSupport {
    @Test
    void persistsEveryAcceptedTechnicalAndCommercialFieldAndReturnsThemOnReplay() throws Exception {
        var body = body();
        body.put("technicalSpecifications", technical());
        body.put("commercialTerms", commercial());
        String key = key();
        var first = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicalSpecifications.powerConnectionCategory")
                        .value("II"))
                .andExpect(jsonPath("$.commercialTerms.fixedRentAmount").value(45000.25))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        var values = jdbc.queryForMap("select building_area,first_floor_area,second_floor_area,basement_area,"
                + "sales_hall_area,floor_slab_height,connected_power,water_supply_flow,power_connection_category,"
                + "sewage_type,lease_terms,fixed_rent_amount,rent_indexation_percent,turnover_payment_percent,"
                + "counterparty_tax_status,construction_term_days,contract_duration_years from objects");
        var response = json.readTree(first);
        technical()
                .forEach((field, value) ->
                        checkValue(response.path("technicalSpecifications").get(field), value));
        commercial()
                .forEach((field, value) ->
                        checkValue(response.path("commercialTerms").get(field), value));
        assertThat(values).hasSize(17).doesNotContainValue(null);
        assertThat(values.get("building_area")).isEqualTo(new java.math.BigDecimal("200.50"));
        assertThat(values.get("contract_duration_years")).isEqualTo(new java.math.BigDecimal("5.50"));
        assertThat(create(manager, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .isEqualTo(first);
        body.put("commercialTerms", Map.of("fixedRentAmount", 12));
        create(manager, key, body).andExpect(status().isConflict());
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
    }

    @Test
    void acceptsPartialEmptyOrOmittedBlocksWithoutCreatingDefaults() throws Exception {
        var body = body();
        body.put("technicalSpecifications", Map.of("connectedPower", 0));
        body.put("commercialTerms", Map.of());
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicalSpecifications.connectedPower").value(0))
                .andExpect(jsonPath("$.technicalSpecifications.buildingArea").isEmpty());
        assertThat(jdbc.queryForObject("select rent_indexation_percent from objects", java.math.BigDecimal.class))
                .isNull();
    }

    private Map<String, Object> technical() {
        return Map.of(
                "buildingArea",
                200.5,
                "firstFloorArea",
                110.5,
                "secondFloorArea",
                90.0,
                "basementArea",
                30.25,
                "salesHallArea",
                80.75,
                "floorSlabHeight",
                3.8,
                "connectedPower",
                100.25,
                "waterSupplyFlow",
                2.5,
                "powerConnectionCategory",
                "II",
                "sewageType",
                "LOCAL_TREATMENT");
    }

    private void checkValue(com.fasterxml.jackson.databind.JsonNode actual, Object expected) {
        if (expected instanceof Number)
            assertThat(new java.math.BigDecimal(actual.asText())).isEqualByComparingTo(expected.toString());
        else assertThat(actual.asText()).isEqualTo(expected.toString());
    }

    private Map<String, Object> commercial() {
        return Map.of(
                "leaseTerms",
                "Оренда на 5 років",
                "fixedRentAmount",
                45000.25,
                "rentIndexationPercent",
                10.5,
                "turnoverPaymentPercent",
                7.25,
                "counterpartyTaxStatus",
                "COMPANY_VAT",
                "constructionTermDays",
                90,
                "contractDurationYears",
                5.5);
    }
}
