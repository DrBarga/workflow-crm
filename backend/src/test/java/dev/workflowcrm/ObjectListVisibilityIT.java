package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectListVisibilityIT extends ObjectListTestSupport {
    @Test
    void managerGetsOwnDetailsAndOnlyPinsOfColleaguesInOwnRegion() throws Exception {
        UUID own = add("Свій", manager, region);
        add("Чужий", colleague, region);
        add("Інший регіон", outsider, otherRegion);
        var result = page(manager, Map.of("sort", "name", "direction", "asc"));
        assertThat(names(result)).containsExactly("Свій", "Чужий");
        var detail = result.path("content").get(0);
        assertThat(detail.path("id").asText()).isEqualTo(own.toString());
        assertThat(detail.path("commercialTerms").path("fixedRentAmount").asInt())
                .isEqualTo(9999);
        assertThat(detail.path("technicalSpecifications").path("buildingArea").asInt())
                .isEqualTo(100);
        assertThat(detail.path("contact").path("phone").asText()).isEqualTo("***");
        assertThat(fields(result.path("content").get(1))).containsExactlyInAnyOrder("name", "entrance");
        request(manager, Map.of()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void regionalLeadGetsFullAssignedRegionsAndRestrictedOthers() throws Exception {
        UUID child = UUID.randomUUID(), grandchild = UUID.randomUUID();
        jdbc.update(
                "insert into regions(id,code,name) values (?,'CHILD','Центр'),(?,'GRAND','Район')", child, grandchild);
        UserRegionTestData.assign(jdbc, lead, child, grandchild);
        add("Батько", colleague, region);
        add("Дитина", manager, child);
        add("Онук", manager, grandchild);
        add("Сторонній", outsider, otherRegion);
        assertThat(names(page(lead, Map.of()))).containsExactlyInAnyOrder("Батько", "Дитина", "Онук", "Сторонній");
        var foreign = page(lead, Map.of("regionId", otherRegion.toString())).path("content");
        assertThat(foreign).hasSize(1);
        assertThat(fields(foreign.get(0))).containsExactlyInAnyOrder("name", "entrance");
        assertThat(names(page(lead, Map.of("regionId", child.toString())))).containsExactly("Дитина");
        assertThat(page(manager, Map.of("regionId", child.toString())).path("content"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"director", "ceo", "admin"})
    void networkRolesGetFullDetailsAcrossAllRegions(String role) throws Exception {
        add("Київ", manager, region);
        add("Дніпро", outsider, otherRegion);
        UUID actor =
                switch (role) {
                    case "director" -> director;
                    case "ceo" -> ceo;
                    default -> admin;
                };
        var result = page(actor, Map.of());
        assertThat(names(result)).containsExactlyInAnyOrder("Київ", "Дніпро");
        result.path("content").forEach(r -> {
            assertThat(r.path("managerId").asText()).isNotBlank();
            assertThat(r.has("workflow")).isTrue();
        });
    }

    @Test
    void contractRoleGetsOnlyTechnicalFieldsForWholeNetworkIncludingEarlyObjects() throws Exception {
        add("Знайдено", manager, region);
        add("Відмова", outsider, otherRegion, "2026-01-15T12:00:00Z", "REFUSED");
        var result = page(contract, Map.of());
        assertThat(names(result)).containsExactlyInAnyOrder("Знайдено", "Відмова");
        result.path("content").forEach(r -> {
            assertThat(fields(r))
                    .containsExactlyInAnyOrder("name", "entrance", "technicalSpecifications", "technicalDescription");
            assertThat(r.path("technicalSpecifications").path("connectedPower").asInt())
                    .isEqualTo(25);
        });
    }

    @Test
    void foreignRegionFilterReturnsEmptyAndDatabaseRoleOverridesJwtRole() throws Exception {
        add("Об’єкт", manager, region);
        assertThat(page(manager, Map.of("regionId", otherRegion.toString())).path("content"))
                .isEmpty();
        jdbc.update(
                "update users set role_id=(select id from roles where code='CONTRACT_MANAGER') where id=?", manager);
        assertThat(fields(page(manager, Map.of()).path("content").get(0)))
                .containsExactlyInAnyOrder("name", "entrance", "technicalSpecifications", "technicalDescription");
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        request(manager, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void missingUserOrMissingRegionCannotExpandAccess() throws Exception {
        add("Об’єкт", colleague, region);
        request(UUID.randomUUID(), Map.of()).andExpect(status().isForbidden());
        UserRegionTestData.replace(jdbc, manager);
        assertThat(page(manager, Map.of()).path("content")).isEmpty();
    }
}
