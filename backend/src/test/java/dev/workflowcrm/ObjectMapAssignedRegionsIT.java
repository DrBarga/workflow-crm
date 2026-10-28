package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectMapAssignedRegionsIT extends ObjectMapTestSupport {
    @Test
    void leadMapSharesListVisibilityAndSearchWithAdditionalAssignments() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", lead, otherRegion);
        add("Primary", colleague, region);
        add("Additional", outsider, otherRegion);
        var third = UUID.randomUUID();
        jdbc.update("insert into regions(id,code,name) values (?,'THIRD','Third')", third);
        add("Foreign", outsider, third);
        assertThat(names(pins(lead))).containsExactlyInAnyOrderElementsOf(names(page(lead, Map.of())));
        pins(lead).path("content").forEach(pin -> {
            boolean foreign = pin.path("name").asText().equals("Foreign");
            assertThat(pin.has("status")).isEqualTo(!foreign);
        });
        assertThat(names(pins(lead, BOX, Map.of("q", "приватна")))).containsExactlyInAnyOrder("Primary", "Additional");
        assertThat(names(pins(lead, BOX, Map.of("regionId", third.toString())))).containsExactly("Foreign");
        UserRegionTestData.replace(jdbc, lead);
        pins(lead).path("content").forEach(pin -> assertThat(pin.has("status")).isFalse());
        assertThat(pins(lead, BOX, Map.of("q", "приватна")).path("content")).isEmpty();
    }

    @Test
    void managerGetsStatusOfOwnObjectsInAdditionalAssignedRegion() throws Exception {
        jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", manager, otherRegion);
        add("Own", manager, otherRegion);
        add("Colleague", outsider, otherRegion);
        var result = pins(manager, BOX, Map.of("regionId", otherRegion.toString()));
        assertThat(names(result)).containsExactlyInAnyOrder("Own", "Colleague");
        result.path("content").forEach(pin -> assertThat(pin.has("status"))
                .isEqualTo(pin.path("name").asText().equals("Own")));
    }
}
