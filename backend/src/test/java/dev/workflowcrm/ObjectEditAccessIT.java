package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectEditAccessIT extends ObjectEditTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void permittedEditorsChangeOwnObject(String role) throws Exception {
        UUID object = add("Початкова назва", manager, region);
        var result = response(
                patch(actor(role), object, change(object, "name", "Нова назва")).andExpect(status().isOk()));
        assertThat(result.path("version").asLong()).isEqualTo(1);
        assertThat(card(manager, object).path("name").asText()).isEqualTo("Нова назва");
    }

    @Test
    void managerCannotEditColleagueOrForeignObject() throws Exception {
        for (UUID owner : List.of(colleague, outsider)) {
            UUID object = add("Об’єкт", owner, owner.equals(outsider) ? otherRegion : region);
            patch(manager, object, change(object, "name", "Нова назва")).andExpect(status().isForbidden());
            assertThat(version(object)).isZero();
        }
    }

    @Test
    void leadCannotEditForeignRegionButDirectorAndAdminCan() throws Exception {
        UUID object = add("Об’єкт", outsider, otherRegion);
        patch(lead, object, change(object, "name", "Нова назва")).andExpect(status().isForbidden());
        patch(director, object, change(object, "name", "Директор")).andExpect(status().isOk());
        patch(admin, object, change(object, "name", "Адміністратор")).andExpect(status().isOk());
    }

    @Test
    void leadCanEditDescendantRegion() throws Exception {
        UUID child = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values (?,'CITY-1','Місто')", child);
        UserRegionTestData.assign(jdbc, lead, child);
        UserRegionTestData.replace(jdbc, colleague, child);
        UUID object = add("Об’єкт", colleague, child);
        patch(lead, object, change(object, "address", "Нова адреса")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "createdAt", "status", "entrance", "contractTypeId"})
    void ceoCannotEditAnyField(String field) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(ceo, object, change(object, field, "значення")).andExpect(status().isForbidden());
        assertThat(version(object)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "address", "createdAt", "status", "technicalSpecifications", "entrance"})
    void contractRoleCannotEditOtherFields(String field) throws Exception {
        UUID object = add("Об’єкт", manager, region);
        patch(contract, object, change(object, field, "значення")).andExpect(status().isForbidden());
    }

    @Test
    void missingDeletedAndInactiveActorHaveCorrectErrors() throws Exception {
        patch(manager, UUID.randomUUID(), Map.of("version", 0, "name", "Нова назва"))
                .andExpect(status().isNotFound());
        UUID object = add("Об’єкт", manager, region);
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id=?", object));
        patch(manager, object, Map.of("version", 0, "name", "Нова назва")).andExpect(status().isNotFound());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        patch(manager, UUID.randomUUID(), Map.of("version", 0, "name", "Нова назва"))
                .andExpect(status().isForbidden());
    }
}
