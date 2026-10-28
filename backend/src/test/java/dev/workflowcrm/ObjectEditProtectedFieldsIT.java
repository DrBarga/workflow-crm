package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditProtectedFieldsIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "createdAt",
                "authorId",
                "status",
                "geoM",
                "googleMapsUrl",
                "entrance",
                "longitude",
                "latitude",
                "regionId",
                "managerId",
                "openingPlannedDate",
                "updatedAt",
                "id"
            })
    void systemFieldsAreProtectedEvenForAdmin(String field) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        var error = response(
                patch(admin, object, change(object, field, "значення")).andExpect(status().isUnprocessableEntity()));
        assertThat(error.path("violations").toString()).contains(field);
        assertThat(version(object)).isZero();
        assertThat(count("object_history")).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"IC_PREPARATION", "COMMERCIAL_TERMS_APPROVAL", "CONTRACT_APPROVAL", "CONTRACT_SIGNED", "OPENED"})
    void managerCanChangeAddressAndTypeAtLateStage(String stage) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, stage);
        patch(manager, object, change(object, "address", "Нова адреса")).andExpect(status().isOk());
        patch(manager, object, change(object, "objectType", "LAND_PLOT")).andExpect(status().isOk());
        assertThat(version(object)).isEqualTo(2);
        assertThat(card(manager, object).path("address").asText()).isEqualTo("Нова адреса");
        assertThat(card(manager, object).path("objectType").asText()).isEqualTo("LAND_PLOT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void privilegedEditorsChangeAddressAndTypeAfterCommittee(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        var body = change(object, "address", "Нова адреса");
        body.put("objectType", "LAND_PLOT");
        patch(actor(role), object, body).andExpect(status().isOk());
        assertThat(card(manager, object).path("objectType").asText()).isEqualTo("LAND_PLOT");
    }

    @Test
    void lateRefusalAndPauseAllowOrdinaryAddressAndTypeEdits() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "PAUSED");
        jdbc.update(
                "update objects set status_before_pause_id=(select id from object_statuses where code='IC_PREPARATION') where id=?",
                object);
        patch(manager, object, change(object, "address", "Нова адреса")).andExpect(status().isOk());
        state(object, "REFUSED");
        jdbc.update(
                "update objects set status_before_refusal_id=(select id from object_statuses where code='IC_PREPARATION') where id=?",
                object);
        patch(manager, object, change(object, "objectType", "LAND_PLOT")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "SUPERADMIN"})
    void contractTypeCanChangeBeforeTasks(String role) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(actor(role), object, change(object, "contractTypeId", type())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select contract_type_id from objects where id=?", UUID.class, object))
                .isEqualTo(type());
    }

    @Test
    void onlyAdminChangesContractTypeAfterContractTemplateSet() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        ObjectContractTaskSetTestData.create(jdbc, object, type(), null, "COMPLETED");
        for (UUID actor : List.of(manager, lead, director, contract))
            patch(actor, object, change(object, "contractTypeId", type())).andExpect(status().isUnprocessableEntity());
        patch(admin, object, change(object, "contractTypeId", type())).andExpect(status().isOk());
    }
}
