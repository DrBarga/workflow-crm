package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectMapVisibilityIT extends ObjectMapTestSupport {
    @Test
    void managerGetsOwnStatusAndForeignPinsWithoutStatusOrPrivateFields() throws Exception {
        UUID own = add("Own", manager, region);
        UUID foreign = add("Foreign", colleague, region);
        add("Other region", outsider, otherRegion);
        var result = pins(manager);
        assertThat(names(result)).containsExactlyInAnyOrder("Own", "Foreign");
        result.path("content").forEach(pin -> {
            boolean owned = pin.path("id").asText().equals(own.toString());
            assertThat(fields(pin))
                    .containsExactlyInAnyOrderElementsOf(
                            owned ? Set.of("id", "name", "entrance", "status") : Set.of("id", "name", "entrance"));
            if (owned) assertThat(pin.path("status").asText()).isEqualTo("FOUND");
            else assertThat(pin.path("id").asText()).isEqualTo(foreign.toString());
            assertThat(fields(pin.path("entrance"))).containsExactlyInAnyOrder("longitude", "latitude");
        });
        mapRequest(manager, BOX, Map.of()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void regionalLeadUsesTheSameAssignmentsAsTheList() throws Exception {
        UUID child = UUID.randomUUID(), grandchild = UUID.randomUUID();
        jdbc.update(
                "insert into regions(id,code,name) values (?,'CHILD','Child'),(?,'GRAND','Grandchild')",
                child,
                grandchild);
        UserRegionTestData.assign(jdbc, lead, child, grandchild);
        add("Root", colleague, region);
        add("Child", manager, child);
        add("Grandchild", manager, grandchild);
        add("Other root", outsider, otherRegion);
        assertThat(names(pins(lead))).containsExactlyInAnyOrderElementsOf(names(page(lead, Map.of())));
        pins(lead).path("content").forEach(pin -> {
            if (pin.path("name").asText().equals("Other root"))
                assertThat(pin.has("status")).isFalse();
            else assertThat(pin.path("status").asText()).isEqualTo("FOUND");
        });
        assertThat(names(pins(lead, BOX, Map.of("regionId", child.toString())))).containsExactly("Child");
        assertThat(names(pins(lead, BOX, Map.of("regionId", otherRegion.toString()))))
                .containsExactly("Other root");
        assertThat(pins(manager, BOX, Map.of("regionId", child.toString())).path("content"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"director", "ceo", "admin"})
    void networkRolesSeeMinimalPinsWithStatusAcrossRegions(String role) throws Exception {
        add("One", manager, region);
        add("Two", outsider, otherRegion);
        UUID actor =
                switch (role) {
                    case "director" -> director;
                    case "ceo" -> ceo;
                    default -> admin;
                };
        var result = pins(actor);
        assertThat(names(result)).containsExactlyInAnyOrder("One", "Two");
        result.path("content")
                .forEach(pin -> assertThat(fields(pin)).containsExactlyInAnyOrder("id", "name", "entrance", "status"));
    }

    @Test
    void contractRoleSeesWholeNetworkWithoutStatusOrTechnicalPayload() throws Exception {
        add("Early", manager, region);
        add("Refused", outsider, otherRegion, "2026-01-15T12:00:00Z", "REFUSED");
        var result = pins(contract);
        assertThat(names(result)).containsExactlyInAnyOrder("Early", "Refused");
        result.path("content")
                .forEach(pin -> assertThat(fields(pin)).containsExactlyInAnyOrder("id", "name", "entrance"));
    }

    @Test
    void databaseRoleAndAssignmentsOverrideJwtAndMissingRegionDoesNotExpandAccess() throws Exception {
        add("Regional", colleague, region);
        add("Network", outsider, otherRegion);
        jdbc.update(
                "update users set role_id=(select id from roles where code='CONTRACT_MANAGER') where id=?", manager);
        assertThat(names(pins(manager))).containsExactlyInAnyOrder("Regional", "Network");
        pins(manager).path("content").forEach(pin -> assertThat(pin.has("status"))
                .isFalse());
        jdbc.update("update users set role_id=(select id from roles where code='MANAGER') where id=?", manager);
        UserRegionTestData.replace(jdbc, manager);
        assertThat(pins(manager).path("content")).isEmpty();
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        mapRequest(manager, BOX, Map.of()).andExpect(status().isForbidden());
        mapRequest(UUID.randomUUID(), BOX, Map.of()).andExpect(status().isForbidden());
    }
}
