package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectListAssignedRegionsIT extends ObjectListTestSupport {
    @Test
    void managerListsBothAssignedRegionsWithOwnDetailsAndColleaguePins() throws Exception {
        assign(manager, otherRegion);
        add("Primary", manager, region);
        add("Additional", manager, otherRegion);
        add("Colleague", outsider, otherRegion);
        var result = page(manager, Map.of("regionId", otherRegion.toString(), "sort", "name", "direction", "asc"));
        assertThat(names(result)).containsExactly("Additional", "Colleague");
        assertThat(result.path("content").get(0).has("workflow")).isTrue();
        assertThat(fields(result.path("content").get(1))).containsExactlyInAnyOrder("name", "entrance");
        assertThat(names(page(manager, Map.of("q", "приватна")))).containsExactlyInAnyOrder("Primary", "Additional");
    }

    @Test
    void leadGetsFullAdditionalAssignmentsAndPinsElsewhere() throws Exception {
        UUID child = UUID.randomUUID(), foreign = UUID.randomUUID();
        jdbc.update(
                "insert into regions(id,code,name) values (?,'EXTRA-CHILD','Child'),(?,'FOREIGN','Foreign')",
                child,
                foreign);
        UserRegionTestData.assign(jdbc, lead, child);
        assign(lead, otherRegion);
        add("Primary", manager, region);
        add("Additional", outsider, otherRegion);
        add("Descendant", outsider, child);
        add("Foreign", outsider, foreign);
        var result = page(lead, Map.of());
        assertThat(names(result)).containsExactlyInAnyOrder("Primary", "Additional", "Descendant", "Foreign");
        result.path("content").forEach(row -> {
            if (row.path("name").asText().equals("Foreign"))
                assertThat(fields(row)).containsExactlyInAnyOrder("name", "entrance");
            else assertThat(row.has("workflow")).isTrue();
        });
    }

    @Test
    void missingAssignmentsGiveLeadOnlyPinsAndDisabledLeadIsDenied() throws Exception {
        add("Object", colleague, region);
        UserRegionTestData.replace(jdbc, lead);
        assertThat(fields(page(lead, Map.of()).path("content").get(0))).containsExactlyInAnyOrder("name", "entrance");
        assertThat(page(lead, Map.of("q", "приватна")).path("content")).isEmpty();
        jdbc.update("update users set status='DISABLED' where id=?", lead);
        request(lead, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void revokedAdditionalAssignmentInvalidatesCursorAndDowngradesNewPage() throws Exception {
        assign(lead, otherRegion);
        add("A", outsider, otherRegion);
        add("B", outsider, otherRegion);
        String cursor = page(lead, Map.of("limit", "1")).path("nextCursor").asText();
        jdbc.update("delete from user_regions where user_id=? and region_id=?", lead, otherRegion);
        request(lead, Map.of("limit", "1", "cursor", cursor)).andExpect(status().isUnprocessableEntity());
        page(lead, Map.of()).path("content").forEach(row -> assertThat(fields(row))
                .containsExactlyInAnyOrder("name", "entrance"));
    }

    private void assign(UUID actor, UUID area) {
        jdbc.update("insert into user_regions(user_id,region_id) values (?,?)", actor, area);
    }
}
