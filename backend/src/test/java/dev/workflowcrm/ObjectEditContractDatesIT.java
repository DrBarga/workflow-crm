package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditContractDatesIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"FOUND", "IC_PREPARATION", "CONTRACT_SIGNED", "PAUSED", "REFUSED"})
    void contractRoleUpdatesAndClearsBothAcceptanceDatesAcrossTheNetwork(String stage) throws Exception {
        UUID object = add("Дати БМР", outsider, otherRegion);
        state(object, stage);
        var body = change(object, "constructionAcceptancePlannedDate", "2026-10-10");
        body.put("constructionAcceptanceActualDate", "2026-10-11");
        patch(contract, object, body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select construction_acceptance_planned_date from objects where id=?", LocalDate.class, object))
                .isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(jdbc.queryForObject(
                        "select construction_acceptance_actual_date from objects where id=?", LocalDate.class, object))
                .isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(state(object)).isEqualTo(stage);
        assertThat(version(object)).isEqualTo(1);
        var editable = card(contract, object).path("editableFields");
        assertThat(editable.path("constructionAcceptancePlannedDate").asBoolean())
                .isTrue();
        assertThat(editable.path("constructionAcceptanceActualDate").asBoolean())
                .isTrue();
        var clear = change(object, "constructionAcceptancePlannedDate", null);
        clear.put("constructionAcceptanceActualDate", null);
        patch(contract, object, clear).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select construction_acceptance_actual_date from objects where id=?", LocalDate.class, object))
                .isNull();
        assertThat(version(object)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where object_id=? and field_name in ('constructionAcceptancePlannedDate','constructionAcceptanceActualDate')",
                        Integer.class,
                        object))
                .isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name",
                "address",
                "objectType",
                "objectArea",
                "landArea",
                "contactId",
                "dataSourceId",
                "technicalSpecifications",
                "commercialTerms",
                "technicalDescription",
                "commercialDescription",
                "openingPlannedDate",
                "openingActualDate",
                "paymentActPlannedDate",
                "paymentActActualDate",
                "entrance",
                "status",
                "createdAt"
            })
    void forbiddenFieldRejectsMixedContractPatchBeforeSavingAcceptanceDates(String field) throws Exception {
        UUID object = add("Захист полів", manager, region);
        var body = change(object, "constructionAcceptanceActualDate", "2026-10-11");
        body.put(field, "forbidden");
        patch(contract, object, body).andExpect(status().isForbidden());
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("object_status_audit")).isZero();
        assertThat(jdbc.queryForObject(
                        "select construction_acceptance_actual_date from objects where id=?", LocalDate.class, object))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "constructionAcceptancePlannedDate",
                "constructionAcceptanceActualDate",
                "contractSignedDate",
                "dataSourceId"
            })
    void ceoCannotEditNewOrExistingContractFields(String field) throws Exception {
        UUID object = add("Лише перегляд", manager, region);
        patch(ceo, object, change(object, field, "2026-10-11")).andExpect(status().isForbidden());
        assertThat(version(object)).isZero();
    }

    @Test
    void staleAndMalformedAcceptanceDatesDoNotWriteAnything() throws Exception {
        UUID object = add("Версія дат", manager, region);
        patch(contract, object, change(object, "constructionAcceptanceActualDate", "bad-date"))
                .andExpect(status().isUnprocessableEntity());
        patch(contract, object, change(object, "constructionAcceptanceActualDate", "2026-10-11"))
                .andExpect(status().isOk());
        patch(contract, object, Map.of("version", 0, "constructionAcceptanceActualDate", "2026-10-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        assertThat(jdbc.queryForObject(
                        "select construction_acceptance_actual_date from objects where id=?", LocalDate.class, object))
                .isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(count("object_history")).isEqualTo(1);
    }
}
